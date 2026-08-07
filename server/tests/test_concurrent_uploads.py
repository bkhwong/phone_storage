"""Regression test for the TOCTOU race between the "does this content_hash already
exist" check and the INSERT in create_asset_from_file: two requests uploading the
same bytes at (almost) the same time must both succeed with a single resulting asset,
never a leaked 500 from an unhandled IntegrityError on the unique content_hash column."""

import hashlib
import io
from concurrent.futures import ThreadPoolExecutor


def test_concurrent_identical_uploads_deduplicate_without_error(client, paired_headers):
    payload = b"same-bytes-uploaded-concurrently-" + (b"z" * 500)
    content_hash = hashlib.sha256(payload).hexdigest()

    def upload(_i: int):
        files = {"file": ("dup.bin", io.BytesIO(payload), "application/octet-stream")}
        data = {
            "content_hash": content_hash,
            "original_filename": "dup.bin",
            "mime_type": "application/octet-stream",
        }
        return client.post("/api/assets/upload", headers=paired_headers, files=files, data=data)

    with ThreadPoolExecutor(max_workers=8) as pool:
        responses = list(pool.map(upload, range(8)))

    for r in responses:
        assert r.status_code == 200, r.text

    asset_ids = {r.json()["id"] for r in responses}
    assert len(asset_ids) == 1, "all concurrent uploads of identical bytes must resolve to one asset"

    r = client.get("/api/assets", headers=paired_headers, params={"limit": 200})
    matching = [a for a in r.json()["items"] if a["content_hash"] == content_hash]
    assert len(matching) == 1, "exactly one asset row should exist for the deduplicated hash"
