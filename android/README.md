# Photo Sync — Android

Kotlin / Jetpack Compose client for Custom Phone ↔ PC Photo Sync.
Targets Samsung S23+ and modern Android (`minSdk 26`, `targetSdk 35`). Sideload via APK (no Play Store).

## Features

- **Pairing** — server URL + PIN → stores `device_token` in EncryptedSharedPreferences
- **MediaStore scan** — Room DB of local assets (`pending` / `backed_up` / `archived` / `discarded`)
- **WorkManager sync** — periodic Wi‑Fi sync (configurable interval; optional cellular)
- **Uploads** — SHA-256 hash, simple multipart for small files, resumable chunked upload for large videos
- **Migration** — user-initiated long-running worker + Continue button for 100GB–1TB first copy
- **Archive / free space** — `POST …/archive` then local delete (`MANAGE_MEDIA` or `createDeleteRequest`)
- **Browse** — timeline grid of server assets (Coil + auth headers); discard archived from app
- **Delete semantics** — Gallery deletes without archive intent → discard on next sync
- **Samsung battery** — guidance screen + dontkillmyapp / settings intents

## Build & install

### Prerequisites

- Android Studio Ladybug+ (or JDK 17 + Android SDK 35)
- PC Photo Sync server running on LAN (see repo `server/`)

### Open & run

1. Open the `android/` folder in Android Studio
2. Let Gradle sync (wrapper uses Gradle 8.9)
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

## First-run checklist

1. Grant photos/videos (+ notifications) permissions
2. Pair with `http://<pc-lan-ip>:8080` and the PIN from the PC
3. Follow **Samsung battery optimization** guidance (Settings or post-pair screen)
4. For a large library, open **Migration** and leave the phone on power + Wi‑Fi; tap **Continue** if Android pauses the transfer
5. Use **Free space** only after items show as backed up

## Permissions

| Permission | Why |
|---|---|
| `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` | Scan library |
| `MANAGE_MEDIA` (optional) | Quieter batch deletes after archive |
| `POST_NOTIFICATIONS` | Migration / sync notifications |
| `FOREGROUND_SERVICE_DATA_SYNC` | Long migration worker |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prompt for unrestricted battery |

## Package layout

```
app/src/main/java/com/phonesync/app/
  data/local/       Room entities + DAO
  data/remote/      Retrofit API + models
  data/prefs/       Encrypted pairing + settings
  data/repository/  Orchestration + delete semantics
  media/            MediaStore scan, hash, deletes
  sync/             UploadEngine, SyncWorker, MigrationWorker
  ui/               Pairing, status, archive, browse, migration, settings, battery
```

## API assumptions (client)

Base URL configurable; auth header `X-Device-Token` after pairing.

- `taken_at` sent as ISO-8601 instant string (or omitted)
- Resumable: `POST /api/uploads/init` returns `upload_id` / `offset` / `chunk_size` / optional `existing_asset_id`
- Chunk body is raw `application/octet-stream` on `PUT …/chunk?offset=`
- Asset `state` query values: `backed_up`, `archived` (and list without filter = all)
- Hash lookup returns `{ "matches": [ { "hash", "asset_id", "state?" } ] }`
- Asset JSON uses `size_bytes`; discard returns `{ "id", "discarded": true }`
- Full contract: [docs/api-contract.md](../docs/api-contract.md)
- Thumbnail/original GET accept `X-Device-Token` (Coil adds the header)

Cleartext HTTP is allowed for LAN (`network_security_config`).
