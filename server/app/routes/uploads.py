from typing import Annotated

from fastapi import APIRouter, Body, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from ..auth import new_id, require_device_token
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


@router.post("/api/uploads/init", response_model=UploadInitResponse)
def init_upload(
    body: UploadInitRequest,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
) -> UploadInitResponse:
    content_hash = body.content_hash.lower()
    if body.size_bytes < 0:
        raise HTTPException(status_code=400, detail="size_bytes must be >= 0")

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

    temp = storage_svc.upload_temp_path(upload_id)
    with temp.open("ab") as f:
        f.write(body)

    session.bytes_received += len(body)
    db.commit()
    return {
        "upload_id": upload_id,
        "offset": session.bytes_received,
        "complete": session.bytes_received >= session.size,
    }


@router.post("/api/uploads/{upload_id}/complete", response_model=AssetResponse)
def complete_upload(
    upload_id: str,
    db: Session = Depends(get_db),
    _device: Device = Depends(require_device_token),
    body: Annotated[UploadCompleteRequest | None, Body()] = None,
) -> AssetResponse:
    session = db.query(UploadSession).filter(UploadSession.id == upload_id).first()
    if not session:
        raise HTTPException(status_code=404, detail="Upload session not found")
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

    asset = create_asset_from_file(
        db,
        src_path=temp,
        content_hash=session.content_hash,
        size=session.size,
        original_filename=session.filename,
        mime_type=session.mime_type,
        taken_at=session.taken_at,
        client_asset_id=session.client_asset_id,
        verify_hash=True,
    )
    session.status = "completed"
    db.commit()
    return _asset_response(asset)


@router.post("/api/assets/upload/chunk")
def chunk_alias_info(_device: Device = Depends(require_device_token)):
    return {
        "message": "Use resumable upload endpoints",
        "init": "POST /api/uploads/init",
        "chunk": "PUT /api/uploads/{id}/chunk?offset=",
        "complete": "POST /api/uploads/{id}/complete",
    }
