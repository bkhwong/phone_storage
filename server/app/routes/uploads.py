import threading
from typing import Annotated

from fastapi import APIRouter, Body, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session
from starlette.concurrency import run_in_threadpool

from ..auth import new_id, require_device_token
from ..config import get_settings
from ..db import get_db
from ..models import Asset, Device, UploadSession
from ..schemas import (
    DEFAULT_CHUNK_SIZE,
    AssetResponse,
    UploadCompleteRequest,
    UploadInitRequest,
    UploadInitResponse,
)
from ..services import storage as storage_svc
from .assets import _asset_response, _parse_taken_at, create_asset_from_file

router = APIRouter(tags=["uploads"], dependencies=[Depends(require_device_token)])

# Serialize chunk writes per upload_id: this is a single-process local server, so an
# in-memory lock per upload is sufficient to prevent two concurrent PUTs from both
# passing the offset check and corrupting the temp file.
_locks_guard = threading.Lock()
_upload_locks: dict[str, threading.Lock] = {}


def _lock_for(upload_id: str) -> threading.Lock:
    with _locks_guard:
        lock = _upload_locks.get(upload_id)
        if lock is None:
            lock = threading.Lock()
            _upload_locks[upload_id] = lock
        return lock


def _forget_lock(upload_id: str) -> None:
    with _locks_guard:
        _upload_locks.pop(upload_id, None)


class _ChunkOffsetConflict(Exception):
    """Raised (under the per-upload lock) when the on-disk file size doesn't match
    the offset the caller expects to write at: either a real offset mismatch or two
    racing requests for the same offset."""

    def __init__(self, actual_size: int):
        self.actual_size = actual_size


def _write_chunk_at_offset(temp_path, offset: int, data: bytes, upload_id: str) -> int:
    """Write `data` at `offset` in temp_path, seeking rather than appending, so
    concurrent/duplicate requests can't corrupt the file. Must run off the event
    loop (it does blocking disk I/O and lock acquisition)."""
    lock = _lock_for(upload_id)
    with lock:
        actual_size = temp_path.stat().st_size if temp_path.exists() else 0
        if actual_size != offset:
            raise _ChunkOffsetConflict(actual_size)
        with temp_path.open("r+b") as f:
            f.seek(offset)
            f.write(data)
            f.truncate(offset + len(data))
        return offset + len(data)


@router.post("/api/uploads/init", response_model=UploadInitResponse)
def init_upload(
    body: UploadInitRequest,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> UploadInitResponse:
    content_hash = body.content_hash.lower()
    if body.size_bytes < 0:
        raise HTTPException(status_code=400, detail="size_bytes must be >= 0")

    settings = get_settings()
    if body.size_bytes > settings.max_upload_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail=(
                f"size_bytes ({body.size_bytes}) exceeds max allowed upload size "
                f"({settings.max_upload_size_bytes} bytes)"
            ),
        )

    existing = db.query(Asset).filter(Asset.content_hash == content_hash).first()
    if existing:
        return UploadInitResponse(
            upload_id="",
            chunk_size=DEFAULT_CHUNK_SIZE,
            offset=existing.size,
            existing_asset_id=existing.id,
        )

    open_session = (
        db.query(UploadSession)
        .filter(
            UploadSession.content_hash == content_hash,
            UploadSession.status == "open",
        )
        .order_by(UploadSession.created_at.desc())
        .first()
    )
    if open_session:
        return UploadInitResponse(
            upload_id=open_session.id,
            chunk_size=DEFAULT_CHUNK_SIZE,
            offset=open_session.bytes_received,
            existing_asset_id=None,
        )

    upload_id = new_id()
    temp = storage_svc.upload_temp_path(upload_id)
    temp.parent.mkdir(parents=True, exist_ok=True)
    temp.write_bytes(b"")

    session = UploadSession(
        id=upload_id,
        content_hash=content_hash,
        size=body.size_bytes,
        filename=body.original_filename,
        mime_type=body.mime_type,
        taken_at=_parse_taken_at(body.taken_at),
        client_asset_id=body.client_asset_id,
        relative_path=body.relative_path,
        bytes_received=0,
        temp_path=temp.as_posix(),
        status="open",
    )
    db.add(session)
    db.commit()
    return UploadInitResponse(
        upload_id=upload_id,
        chunk_size=DEFAULT_CHUNK_SIZE,
        offset=0,
        existing_asset_id=None,
    )


@router.put("/api/uploads/{upload_id}/chunk")
async def put_chunk(
    upload_id: str,
    request: Request,
    offset: int = 0,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
):
    """Accept raw body bytes (application/octet-stream preferred; content-type not enforced)."""
    session = db.query(UploadSession).filter(UploadSession.id == upload_id).first()
    if not session or session.status != "open":
        raise HTTPException(status_code=404, detail="Upload session not found")

    if offset != session.bytes_received:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "message": "offset mismatch",
                "expected_offset": session.bytes_received,
            },
        )

    body = await request.body()
    if not body:
        return {"upload_id": upload_id, "offset": session.bytes_received}

    if session.bytes_received + len(body) > session.size:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="chunk would exceed declared size",
        )

    settings = get_settings()
    if session.bytes_received + len(body) > settings.max_upload_size_bytes:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail="upload exceeds max allowed size",
        )

    temp = storage_svc.upload_temp_path(upload_id)
    try:
        new_size = await run_in_threadpool(_write_chunk_at_offset, temp, offset, body, upload_id)
    except _ChunkOffsetConflict as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail={
                "message": "offset mismatch (concurrent write or drift detected)",
                "expected_offset": exc.actual_size,
            },
        ) from exc

    session.bytes_received = new_size
    db.commit()
    return {
        "upload_id": upload_id,
        "offset": session.bytes_received,
        "complete": session.bytes_received >= session.size,
    }


@router.post("/api/uploads/{upload_id}/complete", response_model=AssetResponse)
async def complete_upload(
    upload_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
    body: Annotated[UploadCompleteRequest | None, Body()] = None,
) -> AssetResponse:
    session = db.query(UploadSession).filter(UploadSession.id == upload_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Upload session not found")
    if session.status == "aborted":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Upload session was aborted (e.g. hash mismatch); call init again",
        )
    if session.status == "completed":
        asset = (
            db.query(Asset)
            .filter(Asset.content_hash == session.content_hash)
            .first()
        )
        if asset:
            return _asset_response(asset)
        raise HTTPException(status_code=409, detail="Session completed but asset missing")

    if session.bytes_received != session.size:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail={
                "message": "upload incomplete",
                "bytes_received": session.bytes_received,
                "size_bytes": session.size,
            },
        )

    if body and body.content_hash:
        if body.content_hash.lower() != session.content_hash.lower():
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="content_hash does not match upload session",
            )

    temp = storage_svc.upload_temp_path(upload_id)
    if not temp.exists():
        raise HTTPException(status_code=404, detail="Temp upload file missing")

    try:
        asset = await run_in_threadpool(
            create_asset_from_file,
            db,
            src_path=temp,
            content_hash=session.content_hash,
            size=session.size,
            original_filename=session.filename,
            mime_type=session.mime_type,
            taken_at=session.taken_at,
            client_asset_id=session.client_asset_id,
            relative_path=session.relative_path,
            verify_hash=True,
        )
    except HTTPException:
        # Don't leave the session stuck "open" forever (e.g. hash mismatch already
        # deleted the temp file) -- abort it so a fresh init starts a clean session.
        session.status = "aborted"
        db.commit()
        _forget_lock(upload_id)
        raise

    session.status = "completed"
    db.commit()
    _forget_lock(upload_id)
    return _asset_response(asset)


@router.post("/api/uploads/{upload_id}/abort")
def abort_upload(
    upload_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
):
    session = db.query(UploadSession).filter(UploadSession.id == upload_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Upload session not found")
    if session.status == "completed":
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Upload already completed; cannot abort",
        )
    if session.status != "aborted":
        session.status = "aborted"
        db.commit()
    storage_svc.upload_temp_path(upload_id).unlink(missing_ok=True)
    _forget_lock(upload_id)
    return {"upload_id": upload_id, "status": "aborted"}


@router.post("/api/assets/upload/chunk")
def chunk_alias_info(_device: Device = Depends(require_device_token)):
    return {
        "message": "Use resumable upload endpoints",
        "init": "POST /api/uploads/init",
        "chunk": "PUT /api/uploads/{id}/chunk?offset=",
        "complete": "POST /api/uploads/{id}/complete",
    }
