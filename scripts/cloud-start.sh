#!/usr/bin/env bash
# Cloud Agent start script: ensure the FastAPI photo-sync server is running.
# Idempotent: a no-op if the server is already healthy, otherwise it launches
# the server in the background, waits for readiness, and returns.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT/server"

HEALTH_URL="http://127.0.0.1:8787/api/health"
LOG_FILE="/tmp/photo-sync-server.log"

if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
  echo "photo-sync server already running at $HEALTH_URL"
  exit 0
fi

nohup ./.venv/bin/python -m app > "$LOG_FILE" 2>&1 &
echo "launched photo-sync server (pid $!); logs at $LOG_FILE"

for _ in $(seq 1 30); do
  if curl -sf "$HEALTH_URL" >/dev/null 2>&1; then
    echo "photo-sync server is up at $HEALTH_URL"
    exit 0
  fi
  sleep 1
done

echo "photo-sync server did not become healthy in time; recent log:" >&2
tail -n 40 "$LOG_FILE" >&2 || true
exit 1
