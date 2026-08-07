# Testing

## Android unit tests (JVM)

From `android/` (JDK 17 + Android SDK required):

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot"  # adjust if needed
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat test
```

Covered without a device:

- `HashUtil.sha256Hex` / `ClientAssetIds`
- `UploadChunking` (chunk size, offset progression, chunk counts) and `skipFully`
- `LocalDeleteSemantics` (gallery delete → discard vs archive intent state machine)
- `SelectionReducer` (Archive screen's select / select-all algebra)
- `formatBytes` / `formatRelativeTime` / `completionFraction` (`ui/common/Formatters.kt`)
- `normalizeBaseUrl` (`data/remote/ApiClientFactory.kt`)
- Pairing helpers: private-LAN-address detection, `friendlyPairError` HTTP-status mapping
- `LocalAssetDao` (Robolectric + in-memory Room DB): count-by-state, `observeArchivable`
  vs. `observeBackedUpTotalCount`, `resetByServerAssetId`, byte sums

90 tests, 0 failures as of this writing.

## Static analysis

```powershell
.\gradlew.bat lintDebug
```

## Android instrumentation / emulator

```powershell
# Create AVD once (API 34 Google APIs x86_64)
avdmanager create avd -n phone_sync_api34 -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force

# Start emulator (separate terminal)
emulator -avd phone_sync_api34 -netdelay none -netspeed full

# When `adb devices` shows the emulator as `device`:
.\gradlew.bat connectedDebugAndroidTest
# or install only:
.\gradlew.bat installDebug
```

Minimal smoke: `SmokeInstrumentedTest` (package name + app string).

If the emulator fails to boot (Hyper-V / WHPX / BIOS virtualization, missing acceleration, or
— as in a cloud sandbox — unstable nested virtualization even with `/dev/kvm` present), JVM
unit tests and `assembleDebug` still apply. Device E2E (pair → upload → archive) remains
manual on a real phone + LAN server.

## Server tests

```bash
cd server
python -m venv .venv && source .venv/bin/activate  # or .venv\Scripts\Activate.ps1 on Windows
pip install -r requirements-dev.txt
python -m pytest -q
```

37 tests, 0 failures as of this writing (`test_health`, `test_pair` + `test_pair_non_reusable`,
`test_auth`, `test_uploads_simple`, `test_uploads_resumable`, `test_upload_cap` (including the
new per-chunk size cap), `test_assets_lifecycle`, `test_assets_listing`, `test_storage_collision`,
`test_concurrent_uploads`).

## Server smoke

```bash
# Server must already be running (PAIR_PIN must match; defaults to 123456).
python scripts/smoke_test.py
# Optional overrides:
#   PHOTO_SYNC_BASE=http://127.0.0.1:8787 PAIR_PIN=123456 python scripts/smoke_test.py
```

Covers health, pair, simple + idempotent upload, hash lookup, thumbnail/original, resumable
chunk upload, mid-upload abort (409 on complete), archive, list by state, and discard.

## Firewall / WoL / second-drive — why hard to automate

| Area | Why CI / unit tests cannot fully cover it | What we can do |
|------|-------------------------------------------|----------------|
| **Firewall** | Needs Administrator rights and a live Windows Defender Firewall + real NIC/LAN. Asserting inbound reachability from a phone requires another host on the same subnet. | Manual checklist in [firewall.md](firewall.md); local `curl` to loopback only proves the server process, not inbound rules. |
| **Wake-on-LAN** | Depends on BIOS/UEFI, NIC WoL support, magic packet on L2 LAN, and often a directed broadcast that guest Wi‑Fi / AP isolation blocks. | Manual steps in [wake-timers-wol.md](wake-timers-wol.md). Not unit-testable in this repo. |
| **backup-storage.ps1** | Needs a real second drive path and `robocopy` I/O. Mirror mode can delete destination files. | `-DryRun` / `-SkipDestinationCheck` parameter validation via `scripts/test-backup-storage.ps1`. Manual checklist: run with real Source/Destination once, verify files, then schedule. |

## Verified in this session (2026-08-07)

A prior version of this file claimed a 2026-07-29 session had run `.\gradlew.bat test`
successfully (30 tests, including a `LocalDeleteSemantics` class) and booted an emulator for
`connectedDebugAndroidTest`. That is not possible as this repo stood at the time: the entire
`android/app/src/main/java/com/phonesync/app/data/` package (Room DB, Retrofit client, prefs,
repository — including `LocalDeleteSemantics`) had never actually been committed (an unanchored
`data/` rule in `.gitignore` silently excluded it — see the git history for the fix), so the
module could not compile, let alone run tests. Treat that entry as unreliable; this entry
reflects what was actually run, with commands and output visible in this session.

Environment: Ubuntu cloud sandbox (no Android Studio); JDK 21 (Temurin/OpenJDK), Android
cmdline-tools + SDK platforms 34/36 + build-tools installed fresh for this session.

| Check | Result |
|-------|--------|
| `./gradlew :app:assembleDebug` | **SUCCESS** |
| `./gradlew :app:testDebugUnitTest` | **90 tests, 0 failures** |
| `./gradlew :app:lintDebug` | **SUCCESS** — all actionable findings fixed (see git log); remaining warnings are minor library-currency suggestions |
| `./gradlew :app:compileDebugAndroidTestKotlin` | **SUCCESS** (compiles; not run — see below) |
| Emulator boot (`emulator -avd ... -no-window`) | **Did not complete** — `/dev/kvm` is present and `vmx` is in `/proc/cpuinfo`, but the guest vCPU faulted (`kvm_spurious_fault` in `dmesg`) and never reached `sys.boot_completed`; nested virtualization is evidently unstable in this particular sandbox. `connectedDebugAndroidTest` and real on-device screenshots were not possible from here as a result. |
| `python -m pytest -q` (server/) | **37 tests, 0 failures** |
| `python scripts/smoke_test.py` (local server) | **PASSED** (health, pair, upload, abort, archive, list, discard) |

## Still manual

- Windows firewall rule on Private profile + phone→PC health
- WoL from phone/router and wake timers
- Full second-drive robocopy (non-dry-run)
- Real-device E2E: pair, sync, archive, Gallery discard semantics, and a visual check of the
  redesigned UI on an actual Samsung Galaxy device (recommended before/instead of relying on
  an emulator, given the instability noted above)
