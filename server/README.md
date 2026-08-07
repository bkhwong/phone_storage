# Custom Photo Sync — PC Server

Local FastAPI server that receives phone photo/video backups over LAN, stores originals on disk, tracks `backed_up` / `archived` state in SQLite, and serves browse/thumbnail/stream APIs.

## Requirements

- Windows 10/11 (LAN host)
- Python 3.11+ recommended
- Free disk space on the drive you set as `STORAGE_ROOT`

## Quick start

```powershell
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
Copy-Item .env.example .env
# Edit .env: STORAGE_ROOT, PAIR_PIN, HOST, PORT, DB_PATH
python -m app
```

Server listens on `http://0.0.0.0:8787` by default. From the phone, use your PC's LAN IP, e.g. `http://192.168.1.42:8787`.

Health check (no auth):

```powershell
curl http://127.0.0.1:8787/api/health
```

Pair (gets `device_token`):

```powershell
curl -X POST http://127.0.0.1:8787/api/pair -H "Content-Type: application/json" -d "{\"pin\":\"123456\"}"
```

## Configuration

| Env var | Meaning |
|---------|---------|
| `STORAGE_ROOT` | Folder for originals (phone-like albums under e.g. `D:\Pictures\Cloud`) plus `.thumbs` / `.uploads` |
| `DB_PATH` | SQLite database file |
| `PAIR_PIN` | PIN the Android app submits to `/api/pair` |
| `PAIR_PIN_REUSABLE` | `true` = multiple devices may pair; `false` = one-time PIN |
| `HOST` / `PORT` | Bind address (use `0.0.0.0` for LAN) |
| `MAX_UPLOAD_SIZE_BYTES` | Reject uploads at/above this total size (default 20 GiB) |
| `MAX_CHUNK_BYTES` | Reject a single chunk PUT body above this size (default 16 MiB); independent, smaller memory-safety cap on top of `MAX_UPLOAD_SIZE_BYTES` |

Copy `.env.example` → `.env` and adjust paths for your machine.

## API overview

Auth header on all routes except health + pair: `X-Device-Token: <token>`

| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/health` | `{ ok, status, version }` |
| POST | `/api/pair` | `{ pin }` → `{ device_token, device_id }` |
| POST | `/api/assets/upload` | multipart: file + hash + metadata |
| POST | `/api/uploads/init` | resumable: `{ content_hash, size_bytes, original_filename, mime_type, ... }` → `{ upload_id, chunk_size, offset, existing_asset_id }` |
| PUT | `/api/uploads/{id}/chunk?offset=` | raw body bytes (`application/octet-stream`) |
| POST | `/api/uploads/{id}/complete` | optional `{ content_hash }` → asset |
| POST | `/api/uploads/{id}/abort` | `{ upload_id, status: "aborted" }`; deletes the temp file |
| GET | `/api/assets?state=&limit=&cursor=` | `{ items, next_cursor }` (asset uses `size_bytes`) |
| GET | `/api/assets/{id}` | metadata |
| GET | `/api/assets/{id}/thumbnail` | JPEG |
| GET | `/api/assets/{id}/original` | file stream |
| POST | `/api/assets/{id}/archive` | state → `archived` (file kept); 409 if missing on disk |
| POST | `/api/assets/{id}/discard` | `{ id, discarded: true }` — delete file + DB row |
| DELETE | `/api/assets/{id}` | same as discard |
| POST | `/api/assets/by-hash/lookup` | `{ hashes }` → `{ matches: [{ hash, asset_id, state }] }` |

Full shapes: [docs/api-contract.md](../docs/api-contract.md).

Asset states: `backed_up` | `archived`. Archive never deletes the PC file.

Duplicate uploads (same `content_hash`) return **200** with the existing asset (idempotent).

Video posters: Pillow image thumbs only; videos get a placeholder JPEG (ffmpeg optional later).

## Security notes

This server is designed for a trusted home LAN, not the open internet:

- Change `PAIR_PIN` from the default before use — the server logs a warning at startup
  if it's still the default. Anyone who can reach the server can pair a device with it.
- Traffic is plain HTTP; device tokens are long-lived and stored in SQLite unencrypted.
  Don't port-forward this server or expose it beyond your LAN.
- Any paired device can see/manage every asset — there's no per-device isolation.
- Pairing rate-limiting and per-upload write locks are in-process (a single `uvicorn`
  worker, which is how `python -m app` runs it) — they don't hold up if you put this
  behind a multi-process/multi-worker server.

## Run at Windows startup

### Option A — Startup folder shortcut

1. Create a `start-photo-sync.bat` next to the venv (edit paths):

```bat
@echo off
cd /d C:\path\to\phone_storage\server
call .venv\Scripts\activate.bat
python -m app
```

2. Press `Win+R` → `shell:startup` → drop a shortcut to that `.bat`.

### Option B — Task Scheduler

1. Task Scheduler → Create Task → **Run whether user is logged on or not** (optional) or at logon.
2. Trigger: At log on.
3. Action: Start a program → `C:\path\to\server\.venv\Scripts\python.exe` with arguments `-m app`, start in `C:\path\to\server`.

## Firewall

Allow inbound TCP on the chosen port (default **8787**) for **Private** networks only. See [docs/firewall.md](../docs/firewall.md).

```powershell
New-NetFirewallRule -DisplayName "Photo Sync Server" -Direction Inbound -Protocol TCP -LocalPort 8787 -Action Allow -Profile Private
```

## Ops docs

- [Pairing](../docs/pairing.md)
- [Firewall](../docs/firewall.md)
- [Wake timers / WoL](../docs/wake-timers-wol.md)
- [Second-drive backup script](../docs/backup-second-drive.md) (`scripts/backup-storage.ps1`)

## Smoke test

With the server running and venv active:

```powershell
python scripts\smoke_test.py
```

(from repo root; see script for details)
