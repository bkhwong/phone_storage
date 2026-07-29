# API contract (server ↔ Android)

Agreed shapes for Custom Photo Sync. Auth on all routes except health and pair:

```http
X-Device-Token: <device_token>
```

Default chunk size: **4 MiB** (`4194304`).

---

## Health

`GET /api/health` (no auth)

```json
{ "ok": true, "status": "ok", "version": "0.1.0" }
```

## Pair

`POST /api/pair`

Request: `{ "pin": "<PAIR_PIN>" }`  
Response: `{ "device_token": "...", "device_id": "..." }`

## Simple upload

`POST /api/assets/upload` (multipart)

Parts: `file`, `content_hash`, `original_filename`, `mime_type`, optional `taken_at` (ISO-8601), optional `client_asset_id`.

Response (asset):

```json
{
  "id": "...",
  "content_hash": "...",
  "state": "backed_up",
  "size_bytes": 12345,
  "original_filename": "...",
  "mime_type": "...",
  "taken_at": null,
  "client_asset_id": "...",
  "created_at": "...",
  "updated_at": "..."
}
```

Same `content_hash` → **200** with the existing asset (idempotent).

## Resumable upload

### Init

`POST /api/uploads/init`

Request (Android names; server also accepts legacy `size` / `filename` / `mime`):

```json
{
  "content_hash": "...",
  "size_bytes": 12345,
  "original_filename": "IMG.jpg",
  "mime_type": "image/jpeg",
  "taken_at": "2026-07-29T12:00:00Z",
  "client_asset_id": "ms:123"
}
```

Response:

```json
{
  "upload_id": "<session id or empty string>",
  "chunk_size": 4194304,
  "offset": 0,
  "existing_asset_id": null
}
```

If the hash already exists: `existing_asset_id` set, `upload_id` empty, `offset` = stored size. Client should skip chunking and mark backed up.

### Chunk

`PUT /api/uploads/{upload_id}/chunk?offset=<n>`

- Body: raw bytes (`Content-Type: application/octet-stream` preferred; not enforced)
- On success: `{ "upload_id", "offset", "complete" }` (clients may ignore body)
- Wrong offset → **409** with `expected_offset`

### Complete

`POST /api/uploads/{upload_id}/complete`

Optional body: `{ "content_hash": "..." }` (verified if present).  
Response: asset object (same as simple upload).

## Hash lookup

`POST /api/assets/by-hash/lookup`

Request: `{ "hashes": ["...", "..."] }` (max 1000)

Response:

```json
{
  "matches": [
    { "hash": "<lowercase sha256>", "asset_id": "...", "state": "backed_up" }
  ]
}
```

## List assets

`GET /api/assets?state=&limit=&cursor=`

- `state`: omit (all) | `backed_up` | `archived`
- `limit`: 1–200 (default 50)
- `cursor`: previous page’s last asset `id`

Response: `{ "items": [ <asset>, ... ], "next_cursor": "<id or null>" }`

Asset fields use `size_bytes` (not `size`).

## Media

- `GET /api/assets/{id}/thumbnail` → JPEG
- `GET /api/assets/{id}/original` → original bytes

## Archive / discard

| Method | Path | Success | Notes |
|--------|------|---------|--------|
| `POST` | `/api/assets/{id}/archive` | **200** asset with `state: "archived"` | File kept on disk; **409** if original missing |
| `POST` | `/api/assets/{id}/discard` | **200** `{ "id", "discarded": true }` | Deletes file + DB row |
| `DELETE` | `/api/assets/{id}` | same as discard | |

---

States: `backed_up` | `archived`.
