"""Anmelden und Abmelden der Eltern."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock
from app.db import get_session
from app.deps import current_parent, current_parent_token
from app.models import Parent, ParentToken
from app.schemas import LoginIn, LoginOut, ParentOut
from app.security import hash_token, new_token, verify_password

router = APIRouter(prefix="/parent", tags=["parent"])


@router.post("/login", response_model=LoginOut)
def login(data: LoginIn, session: Session = Depends(get_session)) -> LoginOut:
    parent = session.scalar(select(Parent).where(Parent.email == data.email.strip().lower()))
    if parent is None or not verify_password(data.password, parent.password_hash):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Anmeldung fehlgeschlagen")
    token = new_token()
    now = clock.now()
    session.add(
        ParentToken(
            parent_id=parent.id, token_hash=hash_token(token), created_at=now, last_used_at=now
        )
    )
    session.commit()
    return LoginOut(token=token, parent=ParentOut.model_validate(parent))


@router.post("/logout", status_code=status.HTTP_204_NO_CONTENT)
def logout(
    token: ParentToken = Depends(current_parent_token), session: Session = Depends(get_session)
) -> Response:
    session.delete(session.get(ParentToken, token.id))
    session.commit()
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/me", response_model=ParentOut)
def me(parent: Parent = Depends(current_parent)) -> ParentOut:
    return ParentOut.model_validate(parent)
