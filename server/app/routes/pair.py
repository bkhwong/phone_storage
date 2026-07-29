from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from ..auth import new_id, new_token
from ..config import get_settings
from ..db import get_db
from ..models import Device
from ..schemas import PairRequest, PairResponse

router = APIRouter(tags=["pair"])


@router.post("/api/pair", response_model=PairResponse)
def pair(body: PairRequest, db: Session = Depends(get_db)) -> PairResponse:
    settings = get_settings()
    if body.pin != settings.pair_pin:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid pairing PIN",
        )

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
