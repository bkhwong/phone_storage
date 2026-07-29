# Optional: wake timers & Wake-on-LAN

The sync app only uploads when the PC server answers on the LAN. Keeping the desktop off most of the day is fine; photos stay on the phone until the next successful backup.

These options are **Phase 2 / optional** — not required for MVP.

## BIOS / UEFI wake timer

Many motherboards can power on at a fixed time (e.g. nightly).

1. Enter BIOS/UEFI (often Del / F2 at boot).
2. Look for **Resume by Alarm**, **RTC Wake**, or **Power On By RTC**.
3. Enable and set a time when you are home on Wi‑Fi (e.g. 01:00).
4. Save and exit. Confirm Windows still starts the photo sync server at logon/startup.

Caveats: Fast Startup / hybrid sleep can interfere; test once after enabling.

## Windows scheduled wake

Task Scheduler can wake the PC to run a task if **Allow wake timers** is enabled in Power Options:

1. Control Panel → Power Options → Change plan settings → Change advanced power settings → Sleep → **Allow wake timers** → Enable.
2. Create a task with “Wake the computer to run this task” checked, and an action that starts the server (or a no-op if the server already runs at logon).

## Wake-on-LAN (WoL)

If your NIC and motherboard support WoL:

1. BIOS: enable Wake on LAN / PCIE wake.
2. Device Manager → Network adapter → Power Management: allow wake; Advanced: Wake on Magic Packet = Enabled.
3. From another device on the LAN (or a small always-on helper), send a magic packet to the PC’s MAC address when you want sync.

Android can send WoL packets from some apps, or a router/script can do it before migration/sync.

## Practical recommendation

- For daily deltas: wake timer or “turn PC on when home” is enough.
- For 100GB–1TB migration: leave the PC on and run a user-initiated long transfer from the phone (chunked uploads).
- Document your PC’s LAN IP and MAC address somewhere private for WoL later.
