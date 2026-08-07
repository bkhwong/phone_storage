"""End-to-end smoke test against a running local server.

Covers the paths the Android client actually uses: health, pair, simple upload
(idempotent), hash lookup, thumbnail/original, resumable chunk upload, abort,
archive, list, and discard. Aligns with docs/api-contract.md.

Environment:
  PHOTO_SYNC_BASE   default http://127.0.0.1:8787
  PAIR_PIN          default 123456 (must match the running server)
"""

from __future__ import annotations

import hashlib
import io
import os
import sys
import time

import httpx

BASE = os.environ.get("PHOTO_SYNC_BASE", "http://127.0.0.1:8787")
PIN = os.environ.get("PAIR_PIN", "123456")


def main() -> int:
    client = httpx.Client(base_url=BASE, timeout=60.0)

    r = client.get("/api/health")
    r.raise_for_status()
    health = r.json()
    assert health.get("ok") is True, health
    print("health:", health)

    r = client.post("/api/pair", json={"pin": PIN})
    r.raise_for_status()
    pair = r.json()
    token = pair["device_token"]
    headers = {"X-Device-Token": token}
    print("paired device_id:", pair["device_id"])

    payload = f"smoke-test-photo-bytes-{time.time_ns()}".encode()
    content_hash = hashlib.sha256(payload).hexdigest()

    files = {"file": ("smoke.jpg", io.BytesIO(payload), "image/jpeg")}
    data = {
        "content_hash": content_hash,
        "taken_at": "2026-07-29T12:00:00Z",
        "original_filename": "smoke.jpg",
        "mime_type": "image/jpeg",
        "client_asset_id": "smoke-1",
        "relative_path": "DCIM/Camera/",
    }
    r = client.post("/api/assets/upload", headers=headers, files=files, data=data)
    r.raise_for_status()
    asset = r.json()
    assert asset["content_hash"] == content_hash
    assert asset["state"] == "backed_up"
    asset_id = asset["id"]
    print("uploaded:", asset_id, "size", asset["size_bytes"])

    # Idempotent re-upload
    files = {"file": ("smoke.jpg", io.BytesIO(payload), "image/jpeg")}
    r = client.post("/api/assets/upload", headers=headers, files=files, data=data)
    r.raise_for_status()
    assert r.json()["id"] == asset_id
    print("idempotent upload ok")

    r = client.post(
        "/api/assets/by-hash/lookup",
        headers=headers,
        json={"hashes": [content_hash, "deadbeef" * 8]},
    )
    r.raise_for_status()
    matches = r.json()["matches"]
    assert any(m["asset_id"] == asset_id for m in matches)
    print("hash lookup ok:", len(matches))

    r = client.get(f"/api/assets/{asset_id}/thumbnail", headers=headers)
    r.raise_for_status()
    assert r.headers["content-type"].startswith("image/jpeg")
    print("thumbnail bytes:", len(r.content))

    r = client.get(f"/api/assets/{asset_id}/original", headers=headers)
    r.raise_for_status()
    assert r.content == payload
    print("original ok")

    # Chunked upload of a second file
    payload2 = b"chunked-" + (b"x" * 10000)
    hash2 = hashlib.sha256(payload2).hexdigest()
    r = client.post(
        "/api/uploads/init",
        headers=headers,
        json={
            "content_hash": hash2,
            "size_bytes": len(payload2),
            "original_filename": "chunk.bin",
            "mime_type": "application/octet-stream",
            "client_asset_id": "smoke-chunk-1",
            "relative_path": "Download/",
        },
    )
    r.raise_for_status()
    init = r.json()
    upload_id = init["upload_id"]
    assert upload_id
    assert init.get("chunk_size", 0) > 0
    assert init.get("existing_asset_id") in (None, "")
    mid = len(payload2) // 2
    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=headers,
        params={"offset": 0},
        content=payload2[:mid],
    )
    r.raise_for_status()
    r = client.put(
        f"/api/uploads/{upload_id}/chunk",
        headers=headers,
        params={"offset": mid},
        content=payload2[mid:],
    )
    r.raise_for_status()
    r = client.post(f"/api/uploads/{upload_id}/complete", headers=headers)
    r.raise_for_status()
    asset2 = r.json()
    print("chunked upload:", asset2["id"])

    # Abort a third resumable session mid-flight (Android migration cancel path)
    payload3 = b"abort-smoke-" + (b"y" * 4000)
    hash3 = hashlib.sha256(payload3).hexdigest()
    r = client.post(
        "/api/uploads/init",
        headers=headers,
        json={
            "content_hash": hash3,
            "size_bytes": len(payload3),
            "original_filename": "abort.bin",
            "mime_type": "application/octet-stream",
        },
    )
    r.raise_for_status()
    abort_id = r.json()["upload_id"]
    assert abort_id
    r = client.put(
        f"/api/uploads/{abort_id}/chunk",
        headers=headers,
        params={"offset": 0},
        content=payload3[:1000],
    )
    r.raise_for_status()
    r = client.post(f"/api/uploads/{abort_id}/abort", headers=headers)
    r.raise_for_status()
    assert r.json()["status"] == "aborted"
    r = client.post(f"/api/uploads/{abort_id}/complete", headers=headers)
    assert r.status_code == 409, r.text
    print("abort mid-upload ok")

    r = client.post(f"/api/assets/{asset_id}/archive", headers=headers)
    r.raise_for_status()
    assert r.json()["state"] == "archived"
    print("archived ok")

    r = client.get("/api/assets", headers=headers, params={"state": "archived"})
    r.raise_for_status()
    assert any(a["id"] == asset_id for a in r.json()["items"])
    print("list archived ok")

    r = client.get("/api/assets", headers=headers, params={"state": "backed_up"})
    r.raise_for_status()
    assert any(a["id"] == asset2["id"] for a in r.json()["items"])
    print("list backed_up ok")

    r = client.post(f"/api/assets/{asset2['id']}/discard", headers=headers)
    r.raise_for_status()
    r = client.get(f"/api/assets/{asset2['id']}", headers=headers)
    assert r.status_code == 404
    print("discard ok")

    print("\nSMOKE TEST PASSED")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except httpx.ConnectError:
        print(
            "Cannot connect to server. Start it first:\n"
            "  cd server && python -m app",
            file=sys.stderr,
        )
        raise SystemExit(1)
