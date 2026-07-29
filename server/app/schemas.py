from datetime import datetime

from pydantic import AliasChoices, BaseModel, ConfigDict, Field


DEFAULT_CHUNK_SIZE = 4 * 1024 * 1024


class HealthResponse(BaseModel):
    ok: bool = True
    status: str = "ok"
    version: str


class PairRequest(BaseModel):
    pin: str


class PairResponse(BaseModel):
    device_token: str
    device_id: str


class AssetResponse(BaseModel):
    id: str
    content_hash: str
    state: str
    size_bytes: int
    original_filename: str | None = None
    mime_type: str | None = None
    taken_at: datetime | None = None
    client_asset_id: str | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None


class AssetListResponse(BaseModel):
    items: list[AssetResponse]
    next_cursor: str | None = None


class HashLookupRequest(BaseModel):
    hashes: list[str] = Field(default_factory=list)


class HashLookupMatch(BaseModel):
    hash: str
    asset_id: str
    state: str | None = None


class HashLookupResponse(BaseModel):
    matches: list[HashLookupMatch] = Field(default_factory=list)


class UploadInitRequest(BaseModel):
    """Accepts Android names; also accepts shorter legacy aliases used by smoke tests."""

    model_config = ConfigDict(populate_by_name=True)

    content_hash: str
    size_bytes: int = Field(validation_alias=AliasChoices("size_bytes", "size"))
    original_filename: str | None = Field(
        default=None,
        validation_alias=AliasChoices("original_filename", "filename"),
    )
    mime_type: str | None = Field(
        default=None,
        validation_alias=AliasChoices("mime_type", "mime"),
    )
    taken_at: str | None = None
    client_asset_id: str | None = None


class UploadInitResponse(BaseModel):
    upload_id: str
    chunk_size: int = DEFAULT_CHUNK_SIZE
    offset: int = 0
    existing_asset_id: str | None = None


class UploadCompleteRequest(BaseModel):
    content_hash: str | None = None


class DiscardResponse(BaseModel):
    id: str
    discarded: bool = True
