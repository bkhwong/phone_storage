import hashlib
import shutil
from datetime import datetime
from pathlib import Path

from ..config import Settings, get_settings


def sha256_file(path: Path, chunk_size: int = 8 * 1024 * 1024) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while True:
            chunk = f.read(chunk_size)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def storage_relative_path(
    content_hash: str,
    original_filename: str | None,
    taken_at: datetime | None = None,
) -> Path:
    """Return relative path under STORAGE_ROOT: YYYY/MM/<hash_prefix>/<hash>_<safe_name>."""
    when = taken_at or datetime.now()
    year = f"{when.year:04d}"
    month = f"{when.month:02d}"
    prefix = content_hash[:2]
    safe = _safe_filename(original_filename) if original_filename else "bin"
    stem = Path(safe).stem[:80] or "bin"
    suffix = Path(safe).suffix[:32]
    name = f"{content_hash}{suffix}" if suffix else f"{content_hash}_{stem}"
    return Path(year) / month / prefix / name


def _safe_filename(name: str) -> str:
    cleaned = "".join(c if c.isalnum() or c in "._-+ " else "_" for c in name)
    cleaned = cleaned.strip(" ._") or "bin"
    return cleaned[:200]


def absolute_storage_path(relative: str | Path, settings: Settings | None = None) -> Path:
    settings = settings or get_settings()
    return (settings.storage_root / Path(relative)).resolve()


def thumb_path_for(asset_id: str, settings: Settings | None = None) -> Path:
    settings = settings or get_settings()
    return settings.storage_root / ".thumbs" / f"{asset_id}.jpg"


def upload_temp_path(upload_id: str, settings: Settings | None = None) -> Path:
    settings = settings or get_settings()
    return settings.storage_root / ".uploads" / upload_id


def place_file(
    src: Path,
    content_hash: str,
    original_filename: str | None,
    taken_at: datetime | None = None,
    settings: Settings | None = None,
) -> Path:
    """Move/copy src into final storage location; returns relative path."""
    settings = settings or get_settings()
    rel = storage_relative_path(content_hash, original_filename, taken_at)
    dest = settings.storage_root / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists():
        # Same hash destination already present — keep existing bytes
        if src.resolve() != dest.resolve():
            src.unlink(missing_ok=True)
        return rel
    shutil.move(str(src), str(dest))
    return rel


def write_bytes_to_final(
    data: bytes,
    content_hash: str,
    original_filename: str | None,
    taken_at: datetime | None = None,
    settings: Settings | None = None,
) -> Path:
    settings = settings or get_settings()
    rel = storage_relative_path(content_hash, original_filename, taken_at)
    dest = settings.storage_root / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    if not dest.exists():
        dest.write_bytes(data)
    return rel
