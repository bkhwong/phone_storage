import tempfile
from datetime import datetime
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from fastapi.responses import FileResponse
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from ..auth import new_id, require_device_token
from ..config import get_settings
from ..db import get_db
from ..models import Asset, Device
from ..schemas import (
    AssetListResponse,
    AssetResponse,
    DiscardResponse,
    HashLookupMatch,
    HashLookupRequest,
    HashLookupResponse,
)
from ..services import storage as storage_svc
from ..services import thumbs as thumbs_svc

router = APIRouter(tags=["assets"], dependencies=[Depends(require_device_token)])


def _safe_absolute_path(relative: str, not_found_detail: str = "Not found") -> Path:
    """absolute_storage_path, but a path-containment violation becomes a 404
    instead of an unhandled 500 (defense in depth for read paths)."""
    try:
        return storage_svc.absolute_storage_path(relative)
    except storage_svc.PathContainmentError as exc:
        raise HTTPException(status_code=404, detail=not_found_detail) from exc


def _asset_response(asset: Asset) -> AssetResponse:
    return AssetResponse(
        id=asset.id,
        content_hash=asset.content_hash,
        state=asset.state,
        size_bytes=asset.size,
        original_filename=asset.original_filename,
        mime_type=asset.mime_type,
        taken_at=asset.taken_at,
        client_asset_id=asset.client_asset_id,
        created_at=asset.created_at,
        updated_at=asset.updated_at,
    )


def _parse_taken_at(value: str | None) -> datetime | None:
    if not value:
        return None
    raw = value.strip()
    if raw.endswith("Z"):
        raw = raw[:-1] + "+00:00"
    try:
        return datetime.fromisoformat(raw)
    except ValueError:
        return None


def create_asset_from_file(
    db: Session,
    *,
    src_path: Path,
    content_hash: str,
    size: int,
    original_filename: str | None,
    mime_type: str | None,
    taken_at: datetime | None,
    client_asset_id: str | None,
    relative_path: str | None = None,
    verify_hash: bool = True,
) -> Asset:
    """Shared create path used by simple upload and chunked complete."""
    existing = db.query(Asset).filter(Asset.content_hash == content_hash).first()
    if existing:
        # Idempotent: drop temp and return existing
        src_path.unlink(missing_ok=True)
        return existing

    if verify_hash:
        actual = storage_svc.sha256_file(src_path)
        if actual.lower() != content_hash.lower():
            src_path.unlink(missing_ok=True)
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail=f"content_hash mismatch: expected {content_hash}, got {actual}",
            )

    rel = storage_svc.place_file(
        src_path,
        content_hash=content_hash.lower(),
        original_filename=original_filename,
        taken_at=taken_at,
        relative_path=relative_path,
    )
    asset_id = new_id()
    abs_path = _safe_absolute_path(rel.as_posix(), "Placed file escaped storage root")
    thumb_abs = storage_svc.thumb_path_for(asset_id)
    thumbs_svc.generate_thumbnail(abs_path, thumb_abs, mime_type=mime_type)

    asset = Asset(
        id=asset_id,
        content_hash=content_hash.lower(),
        state="backed_up",
        original_filename=original_filename,
        mime_type=mime_type,
        size=size,
        storage_path=rel.as_posix(),
        thumbnail_path=f".thumbs/{asset_id}.jpg",
        taken_at=taken_at,
        client_asset_id=client_asset_id,
    )
    db.add(asset)
    db.commit()
    db.refresh(asset)
    return asset


@router.post("/api/assets/upload", response_model=AssetResponse)
async def upload_asset(
    file: UploadFile = File(...),
    content_hash: str = Form(...),
    original_filename: str = Form(...),
    mime_type: str = Form(...),
    taken_at: str | None = Form(None),
    client_asset_id: str | None = Form(None),
    relative_path: str | None = Form(None),
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> AssetResponse:
    existing = db.query(Asset).filter(Asset.content_hash == content_hash.lower()).first()
    if existing:
        # Prefer idempotent 200 with existing asset
        return _asset_response(existing)

    settings = get_settings()
    suffix = Path(original_filename or file.filename or "bin").suffix
    tmp_path: Path | None = None
    try:
        with tempfile.NamedTemporaryFile(
            delete=False, dir=settings.storage_root / ".uploads", suffix=suffix
        ) as tmp:
            tmp_path = Path(tmp.name)
            size = 0
            while True:
                chunk = await file.read(8 * 1024 * 1024)
                if not chunk:
                    break
                size += len(chunk)
                if size > settings.max_upload_size_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_CONTENT_TOO_LARGE,
                        detail="upload exceeds max allowed size",
                    )
                # Blocking disk write off the event loop.
                await run_in_threadpool(tmp.write, chunk)
    except HTTPException:
        if tmp_path is not None:
            tmp_path.unlink(missing_ok=True)
        raise

    try:
        # Hashing, moving into final storage, and thumbnail generation are all
        # blocking; keep them off the event loop.
        asset = await run_in_threadpool(
            create_asset_from_file,
            db,
            src_path=tmp_path,
            content_hash=content_hash,
            size=size,
            original_filename=original_filename or file.filename,
            mime_type=mime_type or file.content_type,
            taken_at=_parse_taken_at(taken_at),
            client_asset_id=client_asset_id,
            relative_path=relative_path,
        )
    except HTTPException:
        raise
    except Exception as exc:
        tmp_path.unlink(missing_ok=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(exc),
        ) from exc

    return _asset_response(asset)


@router.post("/api/assets/by-hash/lookup", response_model=HashLookupResponse)
def lookup_by_hash(
    body: HashLookupRequest,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> HashLookupResponse:
    if not body.hashes:
        return HashLookupResponse(matches=[])
    normalized = [h.lower() for h in body.hashes]
    if len(normalized) > 1000:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Maximum 1000 hashes per lookup",
        )
    rows = db.query(Asset).filter(Asset.content_hash.in_(normalized)).all()
    return HashLookupResponse(
        matches=[
            HashLookupMatch(hash=a.content_hash, asset_id=a.id, state=a.state)
            for a in rows
        ]
    )


@router.get("/api/assets", response_model=AssetListResponse)
def list_assets(
    state: str | None = None,
    limit: int = 50,
    cursor: str | None = None,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> AssetListResponse:
    limit = max(1, min(limit, 200))
    q = db.query(Asset)
    if state:
        if state not in ("backed_up", "archived"):
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="state must be backed_up or archived",
            )
        q = q.filter(Asset.state == state)
    if cursor:
        q = q.filter(Asset.id > cursor)
    rows = q.order_by(Asset.id.asc()).limit(limit + 1).all()
    next_cursor = None
    if len(rows) > limit:
        next_cursor = rows[limit - 1].id
        rows = rows[:limit]
    return AssetListResponse(
        items=[_asset_response(a) for a in rows],
        next_cursor=next_cursor,
    )


@router.get("/api/assets/{asset_id}", response_model=AssetResponse)
def get_asset(
    asset_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> AssetResponse:
    asset = db.query(Asset).filter(Asset.id == asset_id).first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    return _asset_response(asset)


@router.get("/api/assets/{asset_id}/thumbnail")
def get_thumbnail(
    asset_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
):
    asset = db.query(Asset).filter(Asset.id == asset_id).first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    path = _safe_absolute_path(asset.thumbnail_path or f".thumbs/{asset.id}.jpg")
    if not path.exists():
        # Regenerate on demand
        original = _safe_absolute_path(asset.storage_path, "Original missing")
        if not original.exists():
            raise HTTPException(status_code=404, detail="Original missing")
        thumbs_svc.generate_thumbnail(original, path, mime_type=asset.mime_type)
    return FileResponse(path, media_type="image/jpeg", filename=f"{asset_id}.jpg")


@router.get("/api/assets/{asset_id}/original")
def get_original(
    asset_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
):
    asset = db.query(Asset).filter(Asset.id == asset_id).first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    path = _safe_absolute_path(asset.storage_path, "Original missing on disk")
    if not path.exists():
        raise HTTPException(status_code=404, detail="Original missing on disk")
    media = asset.mime_type or "application/octet-stream"
    filename = asset.original_filename or path.name
    return FileResponse(path, media_type=media, filename=filename)


@router.post("/api/assets/{asset_id}/archive", response_model=AssetResponse)
def archive_asset(
    asset_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> AssetResponse:
    asset = db.query(Asset).filter(Asset.id == asset_id).first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    # Confirm file still on disk before phone free-space delete
    path = _safe_absolute_path(asset.storage_path, "Original missing on disk; cannot confirm archive")
    if not path.exists():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Original missing on disk; cannot confirm archive",
        )
    asset.state = "archived"
    db.commit()
    db.refresh(asset)
    return _asset_response(asset)


def _discard_asset(asset_id: str, db: Session) -> DiscardResponse:
    asset = db.query(Asset).filter(Asset.id == asset_id).first()
    if not asset:
        raise HTTPException(status_code=404, detail="Asset not found")
    original = _safe_absolute_path(asset.storage_path)
    thumb = _safe_absolute_path(asset.thumbnail_path or f".thumbs/{asset.id}.jpg")
    original.unlink(missing_ok=True)
    thumb.unlink(missing_ok=True)
    db.delete(asset)
    db.commit()
    return DiscardResponse(id=asset_id, discarded=True)


@router.post("/api/assets/{asset_id}/discard", response_model=DiscardResponse)
def discard_asset(
    asset_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> DiscardResponse:
    return _discard_asset(asset_id, db)


@router.delete("/api/assets/{asset_id}", response_model=DiscardResponse)
def delete_asset(
    asset_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> DiscardResponse:
    return _discard_asset(asset_id, db)
