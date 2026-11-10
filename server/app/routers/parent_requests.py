"""Anfragen der Kinder ansehen und entscheiden — auch erneut, aus der History heraus."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock, events
from app.db import get_session
from app.deps import current_parent
from app.models import (
    EFFECT_ALLOW,
    EFFECT_BLOCK,
    STATUS_APPROVED,
    STATUS_REJECTED,
    Child,
    ContentRequest,
    Parent,
)
from app.rules import find_rule, upsert_rule
from app.schemas import DecisionIn, RequestOut, Status, UtcIn

router = APIRouter(prefix="/parent/requests", tags=["parent"])


def request_out(session: Session, request: ContentRequest) -> RequestOut:
    child = session.get(Child, request.child_id)
    decider = session.get(Parent, request.decided_by) if request.decided_by else None
    return RequestOut(
        id=request.id,
        child_id=request.child_id,
        child_name=child.name if child else "",
        target_type=request.target_type,
        target_id=request.target_id,
        title=request.title,
        channel_id=request.channel_id,
        channel_name=request.channel_name,
        url=request.url,
        thumbnail_url=request.thumbnail_url,
        child_message=request.child_message,
        source_video_id=request.source_video_id,
        source_video_title=request.source_video_title,
        status=request.status,
        decided_by_name=decider.name if decider else None,
        decided_at=request.decided_at,
        reason=request.reason,
        created_at=request.created_at,
    )


@router.get("", response_model=list[RequestOut])
def list_requests(
    child_id: int | None = None,
    status: Status | None = None,
    since: UtcIn | None = None,
    until: UtcIn | None = None,
    limit: int = 200,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> list[RequestOut]:
    # Der Parameter heißt wie in der API „status“; fastapi.status wird hier deshalb nicht importiert.
    query = select(ContentRequest).order_by(ContentRequest.created_at.desc(), ContentRequest.id.desc())
    if child_id is not None:
        query = query.where(ContentRequest.child_id == child_id)
    if status is not None:
        query = query.where(ContentRequest.status == status)
    if since is not None:
        query = query.where(ContentRequest.created_at >= since)
    if until is not None:
        query = query.where(ContentRequest.created_at < until)
    query = query.limit(max(1, min(limit, 500)))
    return [request_out(session, r) for r in session.scalars(query)]


@router.get("/{request_id}", response_model=RequestOut)
def get_request(
    request_id: int, _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> RequestOut:
    return request_out(session, _get_or_404(session, request_id))


def _get_or_404(session: Session, request_id: int) -> ContentRequest:
    request = session.get(ContentRequest, request_id)
    if request is None:
        raise HTTPException(404, "Anfrage nicht gefunden")
    return request


@router.post("/{request_id}/decision", response_model=RequestOut)
def decide_request(
    request_id: int,
    data: DecisionIn,
    parent: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> RequestOut:
    request = _get_or_404(session, request_id)
    child = session.get(Child, request.child_id)
    rule_child_id = None if data.scope == "all" else request.child_id

    if data.action == "approve":
        upsert_rule(session, child_id=rule_child_id, target_type=request.target_type,
                    target_id=request.target_id, effect=EFFECT_ALLOW, title=request.title,
                    channel_name=request.channel_name, parent_id=parent.id)
        request.status = STATUS_APPROVED
    elif data.action == "approve_video_only":
        if request.target_type != "channel" or not request.source_video_id:
            raise HTTPException(400, "Zu dieser Anfrage gibt es kein einzelnes Video")
        upsert_rule(session, child_id=rule_child_id, target_type="video",
                    target_id=request.source_video_id, effect=EFFECT_ALLOW,
                    title=request.source_video_title, channel_name=request.channel_name,
                    parent_id=parent.id)
        request.status = STATUS_APPROVED
    elif data.action == "reject":
        # Eine frühere Freigabe zu genau diesem Ziel zurücknehmen.
        scopes = [request.child_id] + ([None] if data.scope == "all" else [])
        for scope in scopes:
            existing = find_rule(session, scope, request.target_type, request.target_id)
            if existing is not None and existing.effect == EFFECT_ALLOW:
                session.delete(existing)
        request.status = STATUS_REJECTED
    else:  # reject_and_block
        upsert_rule(session, child_id=rule_child_id, target_type=request.target_type,
                    target_id=request.target_id, effect=EFFECT_BLOCK, title=request.title,
                    channel_name=request.channel_name, parent_id=parent.id)
        request.status = STATUS_REJECTED

    request.decided_by = parent.id
    request.decided_at = clock.now()
    request.reason = data.reason.strip()
    session.commit()
    events.decision_made(session, request, child, parent, all_children=data.scope == "all")
    return request_out(session, request)
