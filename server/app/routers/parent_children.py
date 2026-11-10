"""Kinder, ihre Geräte und das Koppeln."""

from __future__ import annotations

import json
from datetime import timedelta

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock, events
from app.config import get_settings
from app.db import get_session
from app.deps import current_parent, get_child_or_404
from app.models import Child, Device, PairingCode, Parent
from app.schemas import ChildCreate, ChildOut, ChildUpdate, DeviceOut, PairingCodeOut
from app.security import hash_token, new_pairing_code, new_topic

router = APIRouter(prefix="/parent", tags=["parent"])

PAIRING_TTL = timedelta(minutes=10)


def child_out(child: Child) -> ChildOut:
    return ChildOut(
        id=child.id,
        name=child.name,
        display_mode=child.display_mode,
        shorts_enabled=child.shorts_enabled,
        reward_active=child.reward_active(clock.now()),
        reward_until=child.reward_until,
        reward_includes_blocked=child.reward_includes_blocked,
    )


@router.get("/children", response_model=list[ChildOut])
def list_children(
    _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> list[ChildOut]:
    return [child_out(c) for c in session.scalars(select(Child).order_by(Child.id))]


@router.post("/children", response_model=ChildOut, status_code=status.HTTP_201_CREATED)
def create_child(
    data: ChildCreate, _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> ChildOut:
    child = Child(
        name=data.name.strip(),
        display_mode="compact",
        shorts_enabled=False,
        reward_until=None,
        reward_includes_blocked=False,
        reward_expiry_notified=True,
        ntfy_topic=new_topic(),
        created_at=clock.now(),
    )
    session.add(child)
    session.commit()
    return child_out(child)


@router.patch("/children/{child_id}", response_model=ChildOut)
def update_child(
    child_id: int,
    data: ChildUpdate,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> ChildOut:
    child = get_child_or_404(session, child_id)
    if data.name is not None:
        child.name = data.name.strip()
    if data.display_mode is not None:
        child.display_mode = data.display_mode
    if data.shorts_enabled is not None:
        child.shorts_enabled = data.shorts_enabled
    session.commit()
    events.sync_child(child)
    return child_out(child)


@router.get("/children/{child_id}/devices", response_model=list[DeviceOut])
def list_devices(
    child_id: int, _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> list[DeviceOut]:
    get_child_or_404(session, child_id)
    devices = session.scalars(
        select(Device).where(Device.child_id == child_id).order_by(Device.id)
    ).all()
    return [DeviceOut.model_validate(d) for d in devices]


@router.delete("/devices/{device_id}", status_code=status.HTTP_204_NO_CONTENT)
def unpair_device(
    device_id: int, _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> Response:
    device = session.get(Device, device_id)
    if device is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Gerät nicht gefunden")
    if device.unpaired_at is None:
        device.unpaired_at = clock.now()
        session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.post("/children/{child_id}/pairing-code", response_model=PairingCodeOut)
def create_pairing_code(
    child_id: int, parent: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> PairingCodeOut:
    get_child_or_404(session, child_id)
    code = new_pairing_code()
    expires_at = clock.now() + PAIRING_TTL
    session.add(
        PairingCode(
            child_id=child_id,
            code_hash=hash_token(code),
            expires_at=expires_at,
            used_at=None,
            created_by=parent.id,
        )
    )
    session.commit()
    payload = json.dumps({"server": get_settings().base_url, "code": code})
    return PairingCodeOut(code=code, expires_at=expires_at, qr_payload=payload)
