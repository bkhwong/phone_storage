import hashlib
import io


def _upload(client, headers, content: bytes, filename: str):
    content_hash = hashlib.sha256(content).hexdigest()
    files = {"file": (filename, io.BytesIO(content), "application/octet-stream")}
    data = {
        "content_hash": content_hash,
        "original_filename": filename,
        "mime_type": "application/octet-stream",
    }
    r = client.post("/api/assets/upload", headers=headers, files=files, data=data)
    assert r.status_code == 200, r.text
    return r.json()


def test_get_unknown_asset_is_404(client, paired_headers):
    r = client.get("/api/assets/does-not-exist", headers=paired_headers)
    assert r.status_code == 404


def test_list_assets_rejects_invalid_state(client, paired_headers):
    r = client.get("/api/assets", headers=paired_headers, params={"state": "bogus"})
    assert r.status_code == 400


def test_list_assets_limit_is_clamped_to_valid_range(client, paired_headers):
    for i in range(3):
        _upload(client, paired_headers, f"content-{i}".encode(), f"f{i}.bin")

    # limit=0 clamps up to 1, not rejected outright.
    r = client.get("/api/assets", headers=paired_headers, params={"limit": 0})
    assert r.status_code == 200
    assert len(r.json()["items"]) == 1

    # limit above the 200 cap clamps down rather than returning everything unbounded.
    r = client.get("/api/assets", headers=paired_headers, params={"limit": 10_000})
    assert r.status_code == 200
    assert len(r.json()["items"]) == 3


def test_list_assets_pagination_cursor_walks_all_items_without_duplicates(client, paired_headers):
    created = [_upload(client, paired_headers, f"page-content-{i}".encode(), f"p{i}.bin") for i in range(5)]
    expected_ids = {a["id"] for a in created}

    seen_ids: set[str] = set()
    cursor = None
    for _ in range(10):  # safety bound against an infinite-loop bug
        params = {"limit": 2}
        if cursor:
            params["cursor"] = cursor
        r = client.get("/api/assets", headers=paired_headers, params=params)
        assert r.status_code == 200
        body = r.json()
        for item in body["items"]:
            assert item["id"] not in seen_ids  # no duplicates across pages
            seen_ids.add(item["id"])
        cursor = body["next_cursor"]
        if not cursor:
            break

    assert seen_ids == expected_ids


def test_list_assets_filters_by_state(client, paired_headers):
    asset = _upload(client, paired_headers, b"state-filter-content", "s.bin")

    r = client.get("/api/assets", headers=paired_headers, params={"state": "archived"})
    assert r.status_code == 200
    assert asset["id"] not in [a["id"] for a in r.json()["items"]]

    r = client.get("/api/assets", headers=paired_headers, params={"state": "backed_up"})
    assert r.status_code == 200
    assert asset["id"] in [a["id"] for a in r.json()["items"]]
