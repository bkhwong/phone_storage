# Second-drive backup of STORAGE_ROOT

A single HDD failure is the main long-term risk. Periodically copy `STORAGE_ROOT` to a second drive (external USB or second internal disk).

## Script

Repo script: [`scripts/backup-storage.ps1`](../scripts/backup-storage.ps1)

```powershell
# From repo root (edit destinations first, or pass params)
.\scripts\backup-storage.ps1 `
  -Source "D:\photo_sync\storage" `
  -Destination "E:\photo_sync_backup\storage"
```

Uses `robocopy` with restartable mode and mirrors extras under the destination tree carefully — by default it **does not** delete destination files that were removed from source (`/E` copy without `/MIR`). Pass `-Mirror` only if you intentionally want a mirror (deletes on dest).

Also copy the SQLite DB if it lives outside `STORAGE_ROOT`:

```powershell
Copy-Item "D:\photo_sync\data\photo_sync.db" "E:\photo_sync_backup\data\photo_sync.db"
```

## Schedule weekly

Task Scheduler → weekly trigger → action:

```
powershell.exe -ExecutionPolicy Bypass -File C:\path\to\phone_storage\scripts\backup-storage.ps1
```

Ensure the destination drive is connected when the task runs.

## Verify

Spot-check file counts:

```powershell
(Get-ChildItem -Recurse -File $Source | Measure-Object).Count
(Get-ChildItem -Recurse -File $Destination | Measure-Object).Count
```
