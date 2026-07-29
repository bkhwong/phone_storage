import hashlib
import re
import shutil
from datetime import datetime
from pathlib import Path

from ..config import Settings, get_settings

_TRAVERSAL = re.compile(r"(^|/|\\)\.\.(/|\\|$)")


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


def album_folder(
    relative_path: str | None,
    original_filename: str | None,
) -> Path:
    """
    Map phone MediaStore relative_path (or filename heuristics) to a folder
    under STORAGE_ROOT that mirrors phone albums.
    """
    cleaned = _sanitize_relative_path(relative_path)
    if cleaned is not None:
        return cleaned

    name = (original_filename or "").lower()
    if name.startswith("screenshot") or name.startswith("screen_") or "screenshot" in name:
        return Path("Pictures") / "Screenshots"
    if name.startswith(("img_", "dsc", "pxl_", "mvimg", "vid_", "video_", "mvi_")):
        return Path("DCIM") / "Camera"
    if name.startswith("whatsapp") or "whatsapp" in name:
        if any(name.endswith(ext) for ext in (".mp4", ".mkv", ".3gp", ".webm", ".mov")):
            return Path("WhatsApp") / "Media" / "WhatsApp Video"
        return Path("WhatsApp") / "Media" / "WhatsApp Images"
    return Path("Other")


def storage_relative_path(
    content_hash: str,
    original_filename: str | None,
    taken_at: datetime | None = None,  # noqa: ARG001 — kept for call-site compatibility
    relative_path: str | None = None,
) -> Path:
    """Return relative path under STORAGE_ROOT: <album>/<original_or_hash_name>."""
    folder = album_folder(relative_path, original_filename)
    safe = _safe_filename(original_filename) if original_filename else "bin"
    stem = Path(safe).stem[:120] or "bin"
    suffix = Path(safe).suffix[:32]
    # Prefer original name; disambiguate collisions with a short hash prefix.
    name = f"{stem}{suffix}" if suffix else stem
    return folder / name


def collision_safe_relative_path(
    dest_rel: Path,
    content_hash: str,
    settings: Settings,
) -> Path:
    """If dest exists with different content, append short hash before suffix."""
    dest = settings.storage_root / dest_rel
    if not dest.exists():
        return dest_rel
    # Same destination already present — caller decides whether to keep.
    short = content_hash[:8]
    stem = dest_rel.stem
    suffix = dest_rel.suffix
    alt = dest_rel.with_name(f"{stem}_{short}{suffix}")
    return alt


def _sanitize_relative_path(relative_path: str | None) -> Path | None:
    if not relative_path:
        return None
    raw = relative_path.replace("\\", "/").strip().strip("/")
    if not raw or _TRAVERSAL.search(raw):
        return None
    parts: list[str] = []
    for part in raw.split("/"):
        part = part.strip()
        if not part or part in (".", ".."):
            continue
        cleaned = "".join(c if c.isalnum() or c in " ._-+" else "_" for c in part)
        cleaned = cleaned.strip(" ._") or "folder"
        parts.append(cleaned[:80])
    if not parts:
        return None
    # Drop trailing filename if client sent full path including file
    # (MediaStore RELATIVE_PATH is directory-only and ends with /).
    return Path(*parts)


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
    relative_path: str | None = None,
    settings: Settings | None = None,
) -> Path:
    """Move/copy src into final storage location; returns relative path."""
    settings = settings or get_settings()
    rel = storage_relative_path(
        content_hash,
        original_filename,
        taken_at=taken_at,
        relative_path=relative_path,
    )
    dest = settings.storage_root / rel
    if dest.exists():
        existing_hash = sha256_file(dest)
        if existing_hash.lower() == content_hash.lower():
            if src.resolve() != dest.resolve():
                src.unlink(missing_ok=True)
            return rel
        rel = collision_safe_relative_path(rel, content_hash, settings)
        dest = settings.storage_root / rel
        if dest.exists():
            if src.resolve() != dest.resolve():
                src.unlink(missing_ok=True)
            return rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(dest))
    return rel


def write_bytes_to_final(
    data: bytes,
    content_hash: str,
    original_filename: str | None,
    taken_at: datetime | None = None,
    relative_path: str | None = None,
    settings: Settings | None = None,
) -> Path:
    settings = settings or get_settings()
    rel = storage_relative_path(
        content_hash,
        original_filename,
        taken_at=taken_at,
        relative_path=relative_path,
    )
    dest = settings.storage_root / rel
    if dest.exists():
        if sha256_file(dest).lower() == content_hash.lower():
            return rel
        rel = collision_safe_relative_path(rel, content_hash, settings)
        dest = settings.storage_root / rel
        if dest.exists():
            return rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return rel
