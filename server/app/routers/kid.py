"""Schnittstelle der Kinder-App."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock, events
from app.config import get_settings
from app.db import get_session
from app.deps import current_child
from app.models import Child, Device, PairingCode
from app.routers.parent_children import child_out
from app.schemas import KidRuleOut, KidStateOut, PairIn, PairOut
from app.security import hash_token, new_token

router = APIRouter(prefix="/kid", tags=["kid"])


@router.post("/pair", response_model=PairOut)
def pair(data: PairIn, session: Session = Depends(get_session)) -> PairOut:
    now = clock.now()
    code = session.scalar(
        select(PairingCode).where(PairingCode.code_hash == hash_token(data.code.strip().upper()))
    )
    if code is None or code.used_at is not None or code.expires_at <= now:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, "Kopplungscode ungültig oder abgelaufen")
    child = session.get(Child, code.child_id)
    code.used_at = now
    token = new_token()
    session.add(
        Device(
            child_id=child.id,
            token_hash=hash_token(token),
            app_version=data.app_version,
            paired_at=now,
            last_seen=now,
        )
    )
    session.commit()
    events.device_paired(session, child)
    return PairOut(
        token=token,
        child_id=child.id,
        child_name=child.name,
        ntfy_url=get_settings().ntfy_public_url,
        ntfy_topic=child.ntfy_topic,
    )


@router.get("/state", response_model=KidStateOut)
def state(child: Child = Depends(current_child), session: Session = Depends(get_session)) -> KidStateOut:
    from app.rules import rules_for_child

    return KidStateOut(
        server_time=clock.now(),
        child=child_out(child),
        rules=[KidRuleOut.model_validate(r) for r in rules_for_child(session, child.id)],
    )
