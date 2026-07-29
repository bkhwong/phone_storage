# Custom Phone ↔ PC Photo Sync

Local LAN backup for phone photos/videos: Android app + PC FastAPI server.

| Path | Owner |
|------|--------|
| `server/` | PC server (this repo half) |
| `android/` | Android app (separate) |
| `docs/` | Pairing, firewall, WoL, second-drive backup |
| `scripts/` | Smoke test + storage backup PowerShell |

See [server/README.md](server/README.md) to run the PC server.

See [docs/testing.md](docs/testing.md) for Android unit/emulator tests and limits of firewall / WoL / backup automation.
