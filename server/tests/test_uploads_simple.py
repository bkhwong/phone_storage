import hashlib
import io


def test_simple_upload_and_idempotent_reupload(client, paired_headers):
    payload = b"hello world image bytes"
    content_hash = hashlib.sha256(payload).hexdigest()
    data = {
        "content_hash": content_hash,
        "original_filename": "photo.jpg",
        "mime_type": "image/jpeg",
    }

    files = {"file": ("photo.jpg", io.BytesIO(payload), "image/jpeg")}
    r = client.post("/api/assets/upload", headers=paired_headers, files=files, data=data)
    assert r.status_code == 200, r.text
    asset = r.json()
    assert asset["content_hash"] == content_hash
    assert asset["state"] == "backed_up"
    asset_id = asset["id"]

    files = {"file": ("photo.jpg", io.BytesIO(payload), "image/jpeg")}
    r = client.post("/api/assets/upload", headers=paired_headers, files=files, data=data)
    assert r.status_code == 200
    assert r.json()["id"] == asset_id

    r = client.get(f"/api/assets/{asset_id}/original", headers=paired_headers)
    assert r.status_code == 200
    assert r.content == payload

    r = client.get(f"/api/assets/{asset_id}/thumbnail", headers=paired_headers)
    assert r.status_code == 200
    assert r.headers["content-type"].startswith("image/jpeg")


def test_hash_lookup(client, paired_headers):
    payload = b"lookup-me-bytes"
    content_hash = hashlib.sha256(payload).hexdigest()
    files = {"file": ("a.jpg", io.BytesIO(payload), "image/jpeg")}
    data = {
        "content_hash": content_hash,
        "original_filename": "a.jpg",
        "mime_type": "image/jpeg",
    }
    r = client.post("/api/assets/upload", headers=paired_headers, files=files, data=data)
    assert r.status_code == 200
    asset_id = r.json()["id"]

    r = client.post(
        "/api/assets/by-hash/lookup",
        headers=paired_headers,
        json={"hashes": [content_hash, "0" * 64]},
    )
    assert r.status_code == 200
    matches = r.json()["matches"]
    assert len(matches) == 1
    assert matches[0]["asset_id"] == asset_id
