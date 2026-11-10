"""Authentifizierung für Eltern und Kinderhandys.

Die beiden Rollen haben getrennte Token-Tabellen — ein Kindertoken kann deshalb nie
als Elterntoken durchgehen.
"""

from __future__ import annotations

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock
from app.db import get_session
from app.models import Child, Device, Parent, ParentToken
from app.security import hash_token

_bearer = HTTPBearer(auto_error=False)


def _unauthorized() -> HTTPException:
    return HTTPException(status.HTTP_401_UNAUTHORIZED, "Nicht angemeldet")


def current_parent_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    session: Session = Depends(get_session),
) -> ParentToken:
    if credentials is None:
        raise _unauthorized()
    token = session.scalar(
        select(ParentToken).where(ParentToken.token_hash == hash_token(credentials.credentials))
    )
    if token is None:
        raise _unauthorized()
    token.last_used_at = clock.now()
    session.commit()
    return token


def current_parent(
    token: ParentToken = Depends(current_parent_token),
    session: Session = Depends(get_session),
) -> Parent:
    parent = session.get(Parent, token.parent_id)
    if parent is None:
        raise _unauthorized()
    return parent


def current_device(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
    session: Session = Depends(get_session),
) -> Device:
    if credentials is None:
        raise _unauthorized()
    device = session.scalar(
        select(Device).where(
            Device.token_hash == hash_token(credentials.credentials),
            Device.unpaired_at.is_(None),
        )
    )
    if device is None:
        raise _unauthorized()
    device.last_seen = clock.now()
    device.stale_notified_at = None
    session.commit()
    return device


def current_child(
    device: Device = Depends(current_device), session: Session = Depends(get_session)
) -> Child:
    child = session.get(Child, device.child_id)
    if child is None:
        raise _unauthorized()
    return child


def get_child_or_404(session: Session, child_id: int) -> Child:
    child = session.get(Child, child_id)
    if child is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Kind nicht gefunden")
    return child
