import hashlib


def test_resumable_upload_full_flow(client, paired_headers):
    payload = b"chunked-upload-" + (b"y" * 5000)
    content_hash = hashlib.sha256(payload).hexdigest()

    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={
            "content_hash": content_hash,
            "size_bytes": len(payload),
            "original_filename": "chunk.bin",
            "mime_type": "application/octet-stream",
        },
    )
    assert r.status_code == 200, r.text
    init = r.json()
    upload_id = init["upload_id"]
    assert upload_id
    assert init["offset"] == 0
    assert init["existing_asset_id"] is None

    mid = len(payload) // 2
    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=payload[:mid],
    )
    assert r.status_code == 200
    assert r.json()["offset"] == mid
    assert r.json()["complete"] is False

    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": mid},
        content=payload[mid:],
    )
    assert r.status_code == 200
    assert r.json()["complete"] is True

    r = client.post(f"/api/uploads/{upload_id}/complete", headers=paired_headers)
    assert r.status_code == 200
    asset = r.json()
    assert asset["content_hash"] == content_hash

    # Completing again is idempotent (returns the already-created asset).
    r = client.post(f"/api/uploads/{upload_id}/complete", headers=paired_headers)
    assert r.status_code == 200
    assert r.json()["id"] == asset["id"]


def test_reinit_returns_same_open_session_at_correct_offset(client, paired_headers):
    payload = b"partial-" + (b"z" * 2000)
    content_hash = hashlib.sha256(payload).hexdigest()
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": content_hash, "size_bytes": len(payload)},
    )
    upload_id = r.json()["upload_id"]

    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=payload[:1000],
    )
    assert r.status_code == 200

    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": content_hash, "size_bytes": len(payload)},
    )
    assert r.status_code == 200
    init2 = r.json()
    assert init2["upload_id"] == upload_id
    assert init2["offset"] == 1000


def test_racing_chunk_writes_at_same_offset_are_rejected(client, paired_headers):
    payload = b"a" * 100
    content_hash = hashlib.sha256(payload).hexdigest()
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": content_hash, "size_bytes": len(payload)},
    )
    upload_id = r.json()["upload_id"]

    r1 = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=payload,
    )
    assert r1.status_code == 200
    assert r1.json()["offset"] == 100

    # A second write believing offset is still 0 (e.g. a stale/racing retry) must
    # be rejected instead of blindly appended and corrupting the file.
    r2 = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=payload,
    )
    assert r2.status_code == 409


def test_hash_mismatch_then_recovery(client, paired_headers):
    real_payload = b"correct-bytes-" + (b"c" * 100)
    wrong_hash = hashlib.sha256(b"totally different content").hexdigest()

    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": wrong_hash, "size_bytes": len(real_payload)},
    )
    upload_id = r.json()["upload_id"]

    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=real_payload,
    )
    assert r.status_code == 200

    r = client.post(f"/api/uploads/{upload_id}/complete", headers=paired_headers)
    assert r.status_code == 400  # hash mismatch

    # The session must not be stuck "open" forever — completing again should give
    # a clear "aborted" conflict, not a 404 (temp file missing) loop.
    r = client.post(f"/api/uploads/{upload_id}/complete", headers=paired_headers)
    assert r.status_code == 409

    # A fresh init for the same (still-wrong) content_hash must get a brand new
    # upload_id, not resurrect the stuck one.
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": wrong_hash, "size_bytes": len(real_payload)},
    )
    assert r.status_code == 200
    new_init = r.json()
    assert new_init["upload_id"] != upload_id
    assert new_init["offset"] == 0

    # And the client can recover by re-initing with the *correct* hash and
    # completing successfully.
    real_hash = hashlib.sha256(real_payload).hexdigest()
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": real_hash, "size_bytes": len(real_payload)},
    )
    upload_id2 = r.json()["upload_id"]
    r = client.put(
        f"/api/uploads/{upload_id2}/chunk",
        headers=paired_headers,
        params={"offset": 0},
        content=real_payload,
    )
    assert r.status_code == 200
    r = client.post(f"/api/uploads/{upload_id2}/complete", headers=paired_headers)
    assert r.status_code == 200
    assert r.json()["content_hash"] == real_hash


def test_abort_upload(client, paired_headers):
    payload = b"abort-me-" + (b"d" * 50)
    content_hash = hashlib.sha256(payload).hexdigest()
    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": content_hash, "size_bytes": len(payload)},
    )
    upload_id = r.json()["upload_id"]

    r = client.post(f"/api/uploads/{upload_id}/abort", headers=paired_headers)
    assert r.status_code == 200
    assert r.json()["status"] == "aborted"

    r = client.post(f"/api/uploads/{upload_id}/complete", headers=paired_headers)
    assert r.status_code == 409

    r = client.post(
        "/api/uploads/init",
        headers=paired_headers,
        json={"content_hash": content_hash, "size_bytes": len(payload)},
    )
    assert r.status_code == 200
    assert r.json()["upload_id"] != upload_id


def test_abort_unknown_upload_404(client, paired_headers):
    r = client.post("/api/uploads/does-not-exist/abort", headers=paired_headers)
    assert r.status_code == 404
