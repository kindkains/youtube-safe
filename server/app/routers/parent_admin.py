"""Eigene Einstellungen und weitere Elternteile."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock
from app.config import get_settings
from app.db import get_session
from app.deps import current_parent
from app.models import Parent
from app.schemas import MeUpdate, ParentCreate, ParentOut, PushInfoOut
from app.security import hash_password, new_topic

router = APIRouter(prefix="/parent", tags=["parent"])


def create_parent(session: Session, name: str, email: str, password: str) -> Parent:
    email = email.strip().lower()
    if session.scalar(select(Parent).where(Parent.email == email)) is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, "E-Mail-Adresse ist schon vergeben")
    parent = Parent(
        name=name.strip(),
        email=email,
        password_hash=hash_password(password),
        ntfy_topic=new_topic(),
        mail_new_request=True,
        mail_alerts=True,
        created_at=clock.now(),
    )
    session.add(parent)
    session.commit()
    return parent


@router.patch("/me", response_model=ParentOut)
def update_me(
    data: MeUpdate,
    parent: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> ParentOut:
    parent = session.get(Parent, parent.id)
    if data.name is not None:
        parent.name = data.name.strip()
    if data.mail_new_request is not None:
        parent.mail_new_request = data.mail_new_request
    if data.mail_alerts is not None:
        parent.mail_alerts = data.mail_alerts
    if data.password is not None:
        parent.password_hash = hash_password(data.password)
    session.commit()
    return ParentOut.model_validate(parent)


@router.get("/push", response_model=PushInfoOut)
def push_info(parent: Parent = Depends(current_parent)) -> PushInfoOut:
    return PushInfoOut(ntfy_url=get_settings().ntfy_public_url, ntfy_topic=parent.ntfy_topic)


@router.get("/parents", response_model=list[ParentOut])
def list_parents(
    _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> list[ParentOut]:
    parents = session.scalars(select(Parent).order_by(Parent.id)).all()
    return [ParentOut.model_validate(p) for p in parents]


@router.post("/parents", response_model=ParentOut, status_code=status.HTTP_201_CREATED)
def add_parent(
    data: ParentCreate,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> ParentOut:
    return ParentOut.model_validate(create_parent(session, data.name, data.email, data.password))
