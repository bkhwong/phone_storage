# Pairing (phone ↔ PC)

The Android app authenticates with a **device token** obtained once via PIN pairing on the LAN. No cloud account.

## Setup on PC

1. Set a PIN in `server/.env`:

   ```
   PAIR_PIN=482913
   PAIR_PIN_REUSABLE=true
   ```

2. Restart the server after changing `.env`.

3. Note the PC LAN IP (Settings → Network, or `ipconfig`). Example: `192.168.1.42`.

4. Confirm health from another device on the same Wi‑Fi:

   ```
   GET http://192.168.1.42:8787/api/health
   ```

## Pair from the phone (or curl)

```http
POST /api/pair
Content-Type: application/json

{ "pin": "482913" }
```

Response:

```json
{
  "device_token": "<store securely on device>",
  "device_id": "<uuid-like id>"
}
```

The app must send on every subsequent request:

```http
X-Device-Token: <device_token>
```

## PIN modes

| `PAIR_PIN_REUSABLE` | Behavior |
|---------------------|----------|
| `true` (default) | Same PIN can pair multiple devices / reinstalls |
| `false` | First successful pair consumes the PIN; further pairs return 409 until you rotate `PAIR_PIN` or clear the `devices` table |

## Security notes

- Keep the server on a **private** LAN (or Tailscale later). Do not port-forward to the public internet in v1.
- Rotate `PAIR_PIN` if you suspect misuse; revoke devices by deleting rows from the `devices` table (or wipe `DB_PATH`).
- Tokens are random (`token_urlsafe`); treat them like passwords on the phone.
