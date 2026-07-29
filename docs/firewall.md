# Windows Firewall

The phone must reach the PC on TCP (default **8787**). Windows Defender Firewall often blocks inbound connections until you add a rule.

## Quick allow (Private profile only)

Run PowerShell **as Administrator**:

```powershell
New-NetFirewallRule `
  -DisplayName "Custom Photo Sync Server" `
  -Direction Inbound `
  -Protocol TCP `
  -LocalPort 8787 `
  -Action Allow `
  -Profile Private
```

If you changed `PORT` in `.env`, use that port instead of `8787`.

## Verify

1. Start the server (`python -m app` from `server/`).
2. From the PC: `curl http://127.0.0.1:8787/api/health`
3. From the phone browser (same Wi‑Fi): `http://<pc-lan-ip>:8787/api/health`

If local works but phone fails:

- Confirm PC and phone are on the same subnet / SSID (guest Wi‑Fi often isolates clients).
- Confirm the network is marked **Private** in Windows (Public profiles are more locked down).
- Temporarily disable third-party antivirus firewalls to test, then re-enable with an exception.

## Remove the rule later

```powershell
Remove-NetFirewallRule -DisplayName "Custom Photo Sync Server"
```

## Do not

- Do not open this port on your router to the internet for v1.
- Prefer Private profile only; avoid Domain/Public unless you know you need them.
