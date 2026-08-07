import hashlib
import io

from sqlalchemy.orm import sessionmaker


def _upload(client, headers, payload: bytes, filename: str = "a.jpg"):
    content_hash = hashlib.sha256(payload).hexdigest()
    files = {"file": (filename, io.BytesIO(payload), "image/jpeg")}
    data = {
        "content_hash": content_hash,
        "original_filename": filename,
        "mime_type": "image/jpeg",
    }
    r = client.post("/api/assets/upload", headers=headers, files=files, data=data)
    assert r.status_code == 200, r.text
    return r.json()


def _delete_asset_file_from_disk(asset_id: str) -> None:
    from app.db import get_engine
    from app.models import Asset as AssetModel
    from app.services import storage as storage_svc

    engine = get_engine()
    session_factory = sessionmaker(bind=engine)
    with session_factory() as db:
        row = db.query(AssetModel).filter(AssetModel.id == asset_id).first()
        assert row is not None
        abs_path = storage_svc.absolute_storage_path(row.storage_path)
    abs_path.unlink()


def test_archive_success(client, paired_headers):
    asset = _upload(client, paired_headers, b"archive-me-bytes")
    r = client.post(f"/api/assets/{asset['id']}/archive", headers=paired_headers)
    assert r.status_code == 200
    assert r.json()["state"] == "archived"

    r = client.get("/api/assets", headers=paired_headers, params={"state": "archived"})
    assert r.status_code == 200
    assert any(a["id"] == asset["id"] for a in r.json()["items"])


def test_archive_conflict_when_file_missing_on_disk(client, paired_headers):
    asset = _upload(client, paired_headers, b"will-be-deleted-from-disk")
    _delete_asset_file_from_disk(asset["id"])

    r = client.post(f"/api/assets/{asset['id']}/archive", headers=paired_headers)
    assert r.status_code == 409


def test_discard(client, paired_headers):
    asset = _upload(client, paired_headers, b"discard-me")
    r = client.post(f"/api/assets/{asset['id']}/discard", headers=paired_headers)
    assert r.status_code == 200
    assert r.json()["discarded"] is True

    r = client.get(f"/api/assets/{asset['id']}", headers=paired_headers)
    assert r.status_code == 404


def test_delete_verb_alias(client, paired_headers):
    asset = _upload(client, paired_headers, b"delete-me-via-delete-verb")
    r = client.delete(f"/api/assets/{asset['id']}", headers=paired_headers)
    assert r.status_code == 200
    assert r.json()["discarded"] is True

    r = client.get(f"/api/assets/{asset['id']}", headers=paired_headers)
    assert r.status_code == 404
