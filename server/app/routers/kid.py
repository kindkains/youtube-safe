"""Schnittstelle der Kinder-App."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock, events
from app.config import get_settings
from app.db import get_session
from app.deps import current_child
from app.models import Child, ContentRequest, Device, PairingCode, STATUS_OPEN
from app.routers.parent_children import child_out
from app.routers.parent_requests import request_out
from app.rules import rules_for_child
from app.schemas import KidRuleOut, KidStateOut, PairIn, PairOut, RequestIn, RequestOut
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
    return KidStateOut(
        server_time=clock.now(),
        child=child_out(child),
        rules=[KidRuleOut.model_validate(r) for r in rules_for_child(session, child.id)],
    )


def _content_url(target_type: str, target_id: str) -> str:
    if target_type == "channel":
        return f"https://www.youtube.com/channel/{target_id}"
    return f"https://www.youtube.com/watch?v={target_id}"


@router.post("/requests", response_model=RequestOut, status_code=status.HTTP_201_CREATED)
def create_request(
    data: RequestIn, child: Child = Depends(current_child), session: Session = Depends(get_session)
) -> RequestOut:
    existing = session.scalar(
        select(ContentRequest).where(
            ContentRequest.child_id == child.id,
            ContentRequest.target_type == data.target_type,
            ContentRequest.target_id == data.target_id,
            ContentRequest.status == STATUS_OPEN,
        )
    )
    if existing is not None:
        return request_out(session, existing)
    request = ContentRequest(
        child_id=child.id,
        target_type=data.target_type,
        target_id=data.target_id,
        title=data.title,
        channel_id=data.channel_id,
        channel_name=data.channel_name,
        url=_content_url(data.target_type, data.target_id),
        thumbnail_url=data.thumbnail_url,
        child_message=data.message.strip(),
        source_video_id=data.source_video_id,
        source_video_title=data.source_video_title,
        status=STATUS_OPEN,
        reason="",
        created_at=clock.now(),
    )
    session.add(request)
    session.commit()
    events.request_created(session, request, child)
    session.commit()  # Outbox-Mails aus request_created
    return request_out(session, request)


@router.get("/requests", response_model=list[RequestOut])
def my_requests(child: Child = Depends(current_child), session: Session = Depends(get_session)) -> list[RequestOut]:
    rows = session.scalars(
        select(ContentRequest)
        .where(ContentRequest.child_id == child.id)
        .order_by(ContentRequest.created_at.desc(), ContentRequest.id.desc())
        .limit(100)
    )
    return [request_out(session, r) for r in rows]
