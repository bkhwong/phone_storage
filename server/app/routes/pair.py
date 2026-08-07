import secrets
import threading
import time
from collections import defaultdict

from fastapi import APIRouter, Depends, HTTPException, Request, status
from sqlalchemy.orm import Session

from ..auth import new_id, new_token
from ..config import get_settings
from ..db import get_db
from ..models import Device
from ..schemas import PairRequest, PairResponse

router = APIRouter(tags=["pair"])

# In-process sliding-window rate limit for pairing attempts: max N failed PIN
# attempts per source IP per window. Fine for a single-process local LAN server.
_RATE_LIMIT_WINDOW_SECONDS = 5 * 60
_RATE_LIMIT_MAX_ATTEMPTS = 5

_attempts_guard = threading.Lock()
_failed_attempts: dict[str, list[float]] = defaultdict(list)


def _client_key(request: Request) -> str:
    return request.client.host if request.client else "unknown"


def _check_rate_limit(key: str) -> None:
    now = time.monotonic()
    with _attempts_guard:
        attempts = [t for t in _failed_attempts.get(key, []) if now - t < _RATE_LIMIT_WINDOW_SECONDS]
        _failed_attempts[key] = attempts
        if len(attempts) >= _RATE_LIMIT_MAX_ATTEMPTS:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many failed pairing attempts; try again later",
            )


def _record_failure(key: str) -> None:
    with _attempts_guard:
        _failed_attempts[key].append(time.monotonic())


def _clear_attempts(key: str) -> None:
    with _attempts_guard:
        _failed_attempts.pop(key, None)


@router.post("/api/pair", response_model=PairResponse)
def pair(body: PairRequest, request: Request, db: Session = Depends(get_db)) -> PairResponse:
    settings = get_settings()
    client_key = _client_key(request)
    _check_rate_limit(client_key)

    if not secrets.compare_digest(body.pin.encode("utf-8"), settings.pair_pin.encode("utf-8")):
        _record_failure(client_key)
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid pairing PIN",
        )
    _clear_attempts(client_key)

    # For non-reusable PIN mode: reject if any device already paired
    if not settings.pair_pin_reusable:
        existing = db.query(Device).first()
        if existing:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="PIN already used; set PAIR_PIN_REUSABLE=true or rotate PAIR_PIN",
            )

    device_id = new_id()
    token = new_token()
    device = Device(id=device_id, token=token, name=None)
    db.add(device)
    db.commit()
    return PairResponse(device_token=token, device_id=device_id)
