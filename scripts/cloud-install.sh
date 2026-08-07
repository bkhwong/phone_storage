#!/usr/bin/env bash
# Cloud Agent install script: prepare the FastAPI photo-sync server.
# Idempotent: safe to run repeatedly against cached or partial state.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT/server"

# The FastAPI server needs Python 3.11+ with venv support. The venv module is
# split into a separate OS package on Debian/Ubuntu, so ensure it is present.
if ! python3 -c "import ensurepip, venv" >/dev/null 2>&1; then
  sudo apt-get update -qq
  sudo apt-get install -y -qq python3-venv
fi

python3 -m venv .venv
./.venv/bin/python -m pip install --upgrade pip
./.venv/bin/pip install -r requirements-dev.txt

echo "photo-sync server dependencies installed in server/.venv"
