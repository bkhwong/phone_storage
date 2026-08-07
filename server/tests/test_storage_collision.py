import hashlib
import io

from app.config import Settings
from app.services import storage as storage_svc


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def test_single_filename_collision_keeps_both_files_intact(client, paired_headers):
    """End-to-end: two different-content uploads sharing the same original
    filename must both survive as distinct, byte-correct assets."""
    payload_a = b"AAAA-original-content-" + (b"a" * 200)
    payload_b = b"BBBB-different-content-" + (b"b" * 200)
    hash_a = _sha256(payload_a)
    hash_b = _sha256(payload_b)
    common = {"original_filename": "photo.jpg", "mime_type": "image/jpeg"}

    files_a = {"file": ("photo.jpg", io.BytesIO(payload_a), "image/jpeg")}
    r = client.post(
        "/api/assets/upload",
        headers=paired_headers,
        files=files_a,
        data={**common, "content_hash": hash_a},
    )
    assert r.status_code == 200, r.text
    asset_a = r.json()

    files_b = {"file": ("photo.jpg", io.BytesIO(payload_b), "image/jpeg")}
    r = client.post(
        "/api/assets/upload",
        headers=paired_headers,
        files=files_b,
        data={**common, "content_hash": hash_b},
    )
    assert r.status_code == 200, r.text
    asset_b = r.json()

    assert asset_a["id"] != asset_b["id"]

    r = client.get(f"/api/assets/{asset_a['id']}/original", headers=paired_headers)
    assert r.status_code == 200
    assert r.content == payload_a

    r = client.get(f"/api/assets/{asset_b['id']}/original", headers=paired_headers)
    assert r.status_code == 200
    assert r.content == payload_b


def test_double_collision_never_drops_the_upload(tmp_path):
    """Unit test for the specific bug: when BOTH the primary destination and the
    hash-suffixed alternate are already occupied by *different* content, place_file
    must keep probing (not silently delete the new upload and misattach it to the
    wrong existing file)."""
    settings = Settings(storage_root=tmp_path / "storage", db_path=tmp_path / "data" / "db.sqlite3")
    settings.ensure_dirs()

    original_filename = "IMG_0001.jpg"
    new_content = b"the-actual-new-upload-bytes-that-must-survive"
    new_hash = _sha256(new_content)

    rel = storage_svc.storage_relative_path(
        content_hash=new_hash,
        original_filename=original_filename,
    )
    primary_dest = settings.storage_root / rel
    primary_dest.parent.mkdir(parents=True, exist_ok=True)
    unrelated_content_1 = b"someone-elses-photo-bytes-at-primary-slot"
    primary_dest.write_bytes(unrelated_content_1)

    short = new_hash[:8]
    hash_suffixed_rel = rel.with_name(f"{rel.stem}_{short}{rel.suffix}")
    hash_suffixed_dest = settings.storage_root / hash_suffixed_rel
    unrelated_content_2 = b"yet-another-unrelated-photo-at-hash-suffixed-slot"
    hash_suffixed_dest.write_bytes(unrelated_content_2)

    src = tmp_path / "incoming.jpg"
    src.write_bytes(new_content)

    final_rel = storage_svc.place_file(
        src,
        content_hash=new_hash,
        original_filename=original_filename,
        settings=settings,
    )

    assert not src.exists()  # moved, not just left behind
    final_abs = settings.storage_root / final_rel
    assert final_abs.exists()
    assert final_abs.read_bytes() == new_content
    # Neither pre-existing file was touched/overwritten.
    assert primary_dest.read_bytes() == unrelated_content_1
    assert hash_suffixed_dest.read_bytes() == unrelated_content_2
    assert final_rel not in (rel, hash_suffixed_rel)


def test_collision_with_byte_identical_content_dedupes_and_drops_src(tmp_path):
    settings = Settings(storage_root=tmp_path / "storage", db_path=tmp_path / "data" / "db.sqlite3")
    settings.ensure_dirs()

    content = b"identical-bytes-both-times"
    content_hash = _sha256(content)
    original_filename = "dup.jpg"

    rel = storage_svc.storage_relative_path(
        content_hash=content_hash,
        original_filename=original_filename,
    )
    dest = settings.storage_root / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(content)

    src = tmp_path / "incoming_dup.jpg"
    src.write_bytes(content)

    final_rel = storage_svc.place_file(
        src,
        content_hash=content_hash,
        original_filename=original_filename,
        settings=settings,
    )

    assert final_rel == rel
    assert not src.exists()
    assert dest.read_bytes() == content
