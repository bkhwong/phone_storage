"""Coverage gap: no test previously asserted that protected routes actually reject
missing/invalid device tokens."""


def test_list_assets_requires_token(client):
    r = client.get("/api/assets")
    assert r.status_code in (401, 403)


def test_list_assets_rejects_invalid_token(client):
    r = client.get("/api/assets", headers={"X-Device-Token": "not-a-real-token"})
    assert r.status_code in (401, 403)


def test_upload_init_requires_token(client):
    r = client.post(
        "/api/uploads/init",
        json={"content_hash": "0" * 64, "size_bytes": 10},
    )
    assert r.status_code in (401, 403)


def test_hash_lookup_requires_token(client):
    r = client.post("/api/assets/by-hash/lookup", json={"hashes": []})
    assert r.status_code in (401, 403)


def test_valid_token_is_accepted(client, paired_headers):
    r = client.get("/api/assets", headers=paired_headers)
    assert r.status_code == 200


def test_health_does_not_require_token(client):
    r = client.get("/api/health")
    assert r.status_code == 200
