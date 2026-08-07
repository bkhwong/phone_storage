import hashlib
import io

import pytest


@pytest.mark.parametrize("client", [{"MAX_UPLOAD_SIZE_BYTES": "1000"}], indirect=True)
def test_init_upload_rejects_oversized_declared_size(client, paired_headers):
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": "0" * 64, "size_bytes": 5000},
    )
    assert r.status_code == 413


@pytest.mark.parametrize("client", [{"MAX_UPLOAD_SIZE_BYTES": "1000"}], indirect=True)
def test_chunk_upload_rejects_when_exceeding_cap(client, paired_headers):
    # Bypass init's own cap check by declaring a size at the cap, then try to push
    # the cumulative bytes_received over the cap via chunk PUTs.
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": "1" * 64, "size_bytes": 1000},
    )
    assert r.status_code == 200
    upload_id = r.json()["upload_id"]

    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=b"x" * 1000,
    )
    assert r.status_code == 200

    # A further chunk beyond the declared size AND the cap should be rejected
    # (declared-size check fires first, which is fine — cap is defense in depth).
    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 1000},
        content=b"y" * 500,
    )
    assert r.status_code in (400, 413)


@pytest.mark.parametrize("client", [{"MAX_UPLOAD_SIZE_BYTES": "1000"}], indirect=True)
def test_simple_upload_rejects_oversized_stream(client, paired_headers):
    payload = b"x" * 5000
    content_hash = hashlib.sha256(payload).hexdigest()
    files = {"file": ("big.bin", io.BytesIO(payload), "application/octet-stream")}
    data = {
        "content_hash": content_hash,
        "original_filename": "big.bin",
        "mime_type": "application/octet-stream",
    }
    r = client.post("/api/assets/upload", headers=paired_headers, files=files, data=data)
    assert r.status_code == 413


@pytest.mark.parametrize("client", [{"MAX_CHUNK_BYTES": "1000"}], indirect=True)
def test_chunk_upload_rejects_body_larger_than_max_chunk_bytes(client, paired_headers):
    """Memory-safety guard: a single PUT .../chunk body is capped independently of the
    declared total asset size, so a client can't force one oversized request to be
    buffered fully into memory (see _read_body_capped in app/routes/uploads.py)."""
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": "2" * 64, "size_bytes": 1_000_000},
    )
    assert r.status_code == 200
    upload_id = r.json()["upload_id"]

    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=b"x" * 2000,
    )
    assert r.status_code == 413

    # The session must still be usable afterwards for a properly-sized chunk.
    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=b"x" * 500,
    )
    assert r.status_code == 200
