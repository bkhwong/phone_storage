import sys
from pathlib import Path

import pytest
from starlette.testclient import TestClient

SERVER_DIR = Path(__file__).resolve().parent.parent
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))


def _reset_app_state(monkeypatch, tmp_path, extra_env=None):
    """Point Settings at an isolated tmp storage/db dir and clear every piece of
    process-global cached/mutable state so each test starts from a clean slate."""
    storage_root = tmp_path / "storage"
    db_path = tmp_path / "data" / "photo_sync.db"
    monkeypatch.setenv("STORAGE_ROOT", str(storage_root))
    monkeypatch.setenv("DB_PATH", str(db_path))
    monkeypatch.setenv("PAIR_PIN", "123456")
    monkeypatch.setenv("PAIR_PIN_REUSABLE", "true")
    for key, value in (extra_env or {}).items():
        monkeypatch.setenv(key, value)

    from app.config import get_settings

    get_settings.cache_clear()

    from app import db as db_module

    db_module._engine = None
    db_module._SessionLocal = None

    from app.routes import pair as pair_module

    pair_module._failed_attempts.clear()

    from app.routes import uploads as uploads_module

    uploads_module._upload_locks.clear()


@pytest.fixture()
def client(request, tmp_path, monkeypatch):
    """A TestClient wired to an isolated storage_root/db_path per test.

    Supports indirect parametrization with a dict of extra env vars, e.g.:
        @pytest.mark.parametrize("client", [{"MAX_UPLOAD_SIZE_BYTES": "1000"}], indirect=True)
    """
    extra_env = getattr(request, "param", None)
    _reset_app_state(monkeypatch, tmp_path, extra_env)

    from app.main import app

    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture()
def paired_headers(client):
    r = client.post("/api/pair", json={"pin": "123456"})
    assert r.status_code == 200, r.text
    token = r.json()["device_token"]
    return {"X-Device-Token": token}
