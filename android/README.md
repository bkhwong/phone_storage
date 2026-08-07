# Photo Sync — Android

Kotlin / Jetpack Compose client for Custom Phone ↔ PC Photo Sync.
Targets Samsung Galaxy / One UI and modern Android (`minSdk 26`, `targetSdk 36`). Sideload via APK (no Play Store).

## Features

- **Pairing** — server URL + PIN → stores `device_token` in EncryptedSharedPreferences
- **MediaStore scan** — Room DB of local assets (`PENDING` / `UPLOADING` / `BACKED_UP` / `ARCHIVED` / `PENDING_DISCARD` / `FAILED`)
- **WorkManager sync** — periodic Wi‑Fi sync (configurable interval; optional cellular); handles Android 14+ "select photos" partial media access
- **Uploads** — SHA-256 hash with a bulk server-side hash lookup to skip re-uploading (reinstalls/rescans), simple multipart for small files, resumable chunked upload for large videos
- **Migration** — user-initiated long-running worker with live byte/item progress + Continue button for 100GB–1TB first copy
- **Archive / free space** — thumbnail grid, `POST …/archive` then local delete (`MANAGE_MEDIA` or `createDeleteRequest`), tracks per-file delete failures instead of assuming success
- **Browse** — grid of server assets (Coil + auth headers) with pinch-zoom viewer; discard archived from app
- **Delete semantics** — Gallery deletes without archive intent → discard on next sync (`LocalDeleteSemantics`)
- **Samsung battery** — guidance screen + dontkillmyapp / settings intents, re-checks status on resume

## Design

Material 3 (dynamic/Material You color on API 31+, a hand-authored fallback palette otherwise)
with a One UI–inspired flat, tonal-card visual language: generous corner radii, no drop
shadows, and a "Device Care"-style circular progress ring on the Home dashboard. Screens are
built on a per-screen `ViewModel` (see `ui/PhotoSyncViewModelFactory.kt`) rather than holding
business logic directly in composables.

## Build & install

### Prerequisites

- Android Studio Ladybug+ (or JDK 17 + Android SDK 36)
- PC Photo Sync server running on LAN (see repo `server/`)

### Open & run

1. Open the `android/` folder in Android Studio
2. Let Gradle sync (wrapper uses Gradle 8.13)
3. Connect the phone (USB debugging) or start an emulator
4. Run the `app` configuration

### CLI

From `android/`:

```bash
# Windows
.\gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

Sideload:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If `local.properties` is missing, create it with your SDK path:

```properties
sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
```

## Tests

See [docs/testing.md](../docs/testing.md) for full details (emulator, firewall/WoL limits).

```powershell
# JVM unit tests (no device) -- includes Robolectric-backed Room DAO tests
.\gradlew.bat test

# Static analysis
.\gradlew.bat lintDebug

# Debug APK
.\gradlew.bat assembleDebug

# Instrumentation (emulator or USB device must be connected)
.\gradlew.bat connectedDebugAndroidTest
```

Unit tests live under `app/src/test/java/`. Instrumentation smoke under `app/src/androidTest/java/`.

## First-run checklist

1. Grant photos/videos (+ notifications) permissions — on Android 14+, choosing "Select
   photos and videos..." instead of "Allow all" is fully supported
2. Pair with `http://<pc-lan-ip>:8787` and the PIN from the PC
3. Follow **Samsung battery optimization** guidance (Settings or post-pair screen)
4. For a large library, open **Migration** and leave the phone on power + Wi‑Fi; tap **Continue** if Android pauses the transfer
5. Use **Free space** only after items show as backed up

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Scan library |
| `READ_MEDIA_VISUAL_USER_SELECTED` | Android 14+ partial ("select photos") access |
| `MANAGE_MEDIA` (optional) | Quieter batch deletes after archive |
| `POST_NOTIFICATIONS` | Migration / sync notifications |
| `FOREGROUND_SERVICE_DATA_SYNC` | Long migration worker |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt for unrestricted battery (sideload-only; see battery guidance screen) |

## Package layout

```
app/src/main/java/com/phonesync/app/
  data/local/       Room entities + DAO + database
  data/remote/      Retrofit API + Moshi DTOs + client factory
  data/prefs/       EncryptedSharedPreferences-backed pairing + settings
  data/repository/  PhotoSyncRepository orchestration + LocalDeleteSemantics
  media/            MediaStore scan, hash, deletes
  sync/             UploadEngine, SyncWorker, MigrationWorker
  ui/               Pairing, status, archive, browse, migration, settings, battery,
                     permissions, common (formatters), components (shared design system),
                     each screen paired with a ViewModel + PhotoSyncViewModelFactory
```

## API assumptions (client)

Base URL configurable; auth header `X-Device-Token` after pairing.

- `taken_at` sent as ISO-8601 instant string (or omitted)
- Resumable: `POST /api/uploads/init` returns `upload_id` / `offset` / `chunk_size` / optional `existing_asset_id`
- Chunk body is raw `application/octet-stream` on `PUT …/chunk?offset=`; a single chunk is capped server-side independent of total upload size (`MAX_CHUNK_BYTES`)
- Asset `state` query values: `backed_up`, `archived` (and list without filter = all)
- Hash lookup returns `{ "matches": [ { "hash", "asset_id", "state?" } ] }`
- Asset JSON uses `size_bytes`; discard returns `{ "id", "discarded": true }`
- Full contract: [docs/api-contract.md](../docs/api-contract.md)
- Thumbnail/original GET accept `X-Device-Token` (Coil adds the header)

Cleartext HTTP is allowed for LAN (`network_security_config`).
