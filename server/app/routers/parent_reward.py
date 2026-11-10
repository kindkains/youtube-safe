"""Belohnungsmodus — immer zeitlich begrenzt."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app import clock, events
from app.config import get_settings
from app.db import get_session
from app.deps import current_parent, get_child_or_404
from app.models import Parent
from app.routers.parent_children import child_out
from app.schemas import MAX_REWARD_MINUTES, ChildOut, RewardIn

router = APIRouter(prefix="/parent/children", tags=["parent"])


def _local_text(utc_value: datetime) -> str:
    local = utc_value.replace(tzinfo=timezone.utc).astimezone(ZoneInfo(get_settings().timezone))
    return local.strftime("%d.%m. %H:%M")


@router.post("/{child_id}/reward", response_model=ChildOut)
def start_reward(
    child_id: int,
    data: RewardIn,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> ChildOut:
    child = get_child_or_404(session, child_id)
    now = clock.now()
    until = now + timedelta(minutes=data.minutes) if data.minutes is not None else data.until
    if until <= now:
        raise HTTPException(400, "Das Ende muss in der Zukunft liegen")
    if until - now > timedelta(minutes=MAX_REWARD_MINUTES):
        raise HTTPException(400, "Höchstens 30 Tage am Stück")
    child.reward_until = until
    child.reward_includes_blocked = data.include_blocked
    child.reward_expiry_notified = False
    session.commit()
    events.reward_started(child, _local_text(until))
    return child_out(child)


@router.delete("/{child_id}/reward", response_model=ChildOut)
def stop_reward(
    child_id: int, _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> ChildOut:
    child = get_child_or_404(session, child_id)
    child.reward_until = None
    child.reward_includes_blocked = False
    child.reward_expiry_notified = True
    session.commit()
    events.reward_stopped(child)
    return child_out(child)
