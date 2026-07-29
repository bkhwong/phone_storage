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
- Moshi serialization for pair, upload init, hash lookup `matches`, discard
- `LocalDeleteSemantics` (gallery delete → discard vs archive intent)
- `UploadChunking` (chunk size, offset progression, chunk counts)

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

If the emulator fails to boot (Hyper-V / WHPX / BIOS virtualization, missing acceleration), JVM unit tests and `assembleDebug` still apply. Device E2E (pair → upload → archive) remains manual on a real phone + LAN server.

## Server smoke

```powershell
python scripts/smoke_test.py
```

## Firewall / WoL / second-drive — why hard to automate

| Area | Why CI / unit tests cannot fully cover it | What we can do |
|------|-------------------------------------------|----------------|
| **Firewall** | Needs Administrator rights and a live Windows Defender Firewall + real NIC/LAN. Asserting inbound reachability from a phone requires another host on the same subnet. | Manual checklist in [firewall.md](firewall.md); local `curl` to loopback only proves the server process, not inbound rules. |
| **Wake-on-LAN** | Depends on BIOS/UEFI, NIC WoL support, magic packet on L2 LAN, and often a directed broadcast that guest Wi‑Fi / AP isolation blocks. | Manual steps in [wake-timers-wol.md](wake-timers-wol.md). Not unit-testable in this repo. |
| **backup-storage.ps1** | Needs a real second drive path and `robocopy` I/O. Mirror mode can delete destination files. | `-DryRun` / `-SkipDestinationCheck` parameter validation via `scripts/test-backup-storage.ps1`. Manual checklist: run with real Source/Destination once, verify files, then schedule. |


## Verified in this session (2026-07-29)

| Check | Result |
|-------|--------|
| JDK | Microsoft OpenJDK 17.0.20 at `C:\Program Files\Microsoft\jdk-17.0.20.8-hotspot` (not on PATH by default; set `JAVA_HOME`) |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` — platforms 35, build-tools 34/35, platform-tools, cmdline-tools |
| Installed this session | `emulator`, `system-images;android-34;google_apis;x86_64`; AVD `phone_sync_api34` |
| `.\gradlew.bat test` (debug unit) | **30 tests, 0 failures** (`HashUtil`, Moshi models, `LocalDeleteSemantics`, `UploadChunking`) |
| `.\gradlew.bat assembleDebug` | **SUCCESS** |
| Emulator | Booted (~143s); `adb` showed `emulator-5554 device` |
| `installDebug` + `connectedDebugAndroidTest` | **SUCCESS** — 2 instrumentation smoke tests passed |
| `scripts/test-backup-storage.ps1` | **8 assertions passed** (DryRun / Mirror / same-path rejection) |
## Still manual

- Windows firewall rule on Private profile + phone→PC health
- WoL from phone/router and wake timers
- Full second-drive robocopy (non-dry-run)
- Real-device E2E: pair, sync, archive, Gallery discard semantics
