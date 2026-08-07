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


def resolve_destination_for_hash(
    rel: Path,
    content_hash: str,
    settings: Settings,
) -> tuple[Path, bool]:
    """
    Find a final relative path under storage_root for content_hash, starting from `rel`.

    Returns (final_rel, reuse_existing):
      - reuse_existing=True: final_rel already exists on disk with byte-identical
        content (verified by hash) — caller should dedupe against it and drop src.
      - reuse_existing=False: final_rel is a free slot — caller should write/move
        the new content there.

    Never returns a path holding *different* content than content_hash: if the
    hash-suffixed alternate is also taken by different content, we keep probing
    numbered suffixes (_2, _3, ...) until we find a matching or free slot, instead
    of silently discarding the upload.
    """
    dest = settings.storage_root / rel
    if not dest.exists():
        return rel, False
    if sha256_file(dest).lower() == content_hash.lower():
        return rel, True

    short = content_hash[:8]
    stem = rel.stem
    suffix = rel.suffix
    candidate = rel.with_name(f"{stem}_{short}{suffix}")
    counter = 2
    while True:
        dest = settings.storage_root / candidate
        if not dest.exists():
            return candidate, False
        if sha256_file(dest).lower() == content_hash.lower():
            return candidate, True
        candidate = rel.with_name(f"{stem}_{short}_{counter}{suffix}")
        counter += 1


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


class PathContainmentError(ValueError):
    """Raised when a resolved path would escape storage_root."""


def absolute_storage_path(relative: str | Path, settings: Settings | None = None) -> Path:
    settings = settings or get_settings()
    root = settings.storage_root.resolve()
    candidate = (root / Path(relative)).resolve()
    if not (candidate == root or candidate.is_relative_to(root)):
        raise PathContainmentError(f"Resolved path escapes storage root: {relative!r}")
    return candidate


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
    final_rel, reuse_existing = resolve_destination_for_hash(rel, content_hash, settings)
    dest = settings.storage_root / final_rel
    if reuse_existing:
        if src.resolve() != dest.resolve():
            src.unlink(missing_ok=True)
        return final_rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.move(str(src), str(dest))
    return final_rel


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
    final_rel, reuse_existing = resolve_destination_for_hash(rel, content_hash, settings)
    if reuse_existing:
        return final_rel
    dest = settings.storage_root / final_rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
    return final_rel
