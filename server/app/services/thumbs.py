from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


IMAGE_MIME_PREFIXES = ("image/",)
# Video posters via ffmpeg are optional; we use a placeholder JPEG if not an image.


def generate_thumbnail(
    source: Path,
    dest: Path,
    mime_type: str | None = None,
    max_size: int = 512,
) -> bool:
    """Create a JPEG thumbnail at dest. Returns True if a real image thumb was made."""
    dest.parent.mkdir(parents=True, exist_ok=True)
    mime = (mime_type or "").lower()
    is_image = mime.startswith("image/") or _looks_like_image(source)

    if is_image:
        try:
            with Image.open(source) as im:
                im = im.convert("RGB")
                im.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)
                im.save(dest, "JPEG", quality=85, optimize=True)
            return True
        except Exception:
            pass

    _write_placeholder(dest, label=_label_for(source, mime), max_size=max_size)
    return False


def _looks_like_image(path: Path) -> bool:
    return path.suffix.lower() in {
        ".jpg",
        ".jpeg",
        ".png",
        ".gif",
        ".webp",
        ".bmp",
        ".heic",
        ".tif",
        ".tiff",
    }


def _label_for(path: Path, mime: str) -> str:
    if mime.startswith("video/") or path.suffix.lower() in {
        ".mp4",
        ".mov",
        ".mkv",
        ".avi",
        ".webm",
        ".3gp",
    }:
        return "VIDEO"
    return "FILE"


def _write_placeholder(dest: Path, label: str, max_size: int = 512) -> None:
    img = Image.new("RGB", (max_size, max_size), color=(40, 44, 52))
    draw = ImageDraw.Draw(img)
    text = label
    # Default font; size approximation via textbbox when available
    try:
        font = ImageFont.load_default()
        bbox = draw.textbbox((0, 0), text, font=font)
        tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    except Exception:
        tw, th = len(text) * 6, 11
        font = None
    x = (max_size - tw) // 2
    y = (max_size - th) // 2
    draw.text((x, y), text, fill=(200, 200, 210), font=font)
    img.save(dest, "JPEG", quality=80)
