"""Freigaben und Sperren verwalten."""

from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Response, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app import clock, events
from app.db import get_session
from app.deps import current_parent, get_child_or_404
from app.models import Parent, Rule
from app.rules import upsert_rule
from app.schemas import RuleIn, RuleOut, RuleUpdate

router = APIRouter(prefix="/parent/rules", tags=["parent"])


def _get_rule_or_404(session: Session, rule_id: int) -> Rule:
    rule = session.get(Rule, rule_id)
    if rule is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Regel nicht gefunden")
    return rule


@router.get("", response_model=list[RuleOut])
def list_rules(
    child_id: int | None = None,
    global_only: bool = False,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> list[RuleOut]:
    query = select(Rule).order_by(Rule.updated_at.desc(), Rule.id.desc())
    if global_only:
        query = query.where(Rule.child_id.is_(None))
    elif child_id is not None:
        query = query.where(or_(Rule.child_id == child_id, Rule.child_id.is_(None)))
    return [RuleOut.model_validate(r) for r in session.scalars(query)]


@router.post("", response_model=RuleOut, status_code=status.HTTP_201_CREATED)
def create_rule(
    data: RuleIn, parent: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> RuleOut:
    if data.child_id is not None:
        get_child_or_404(session, data.child_id)
    rule = upsert_rule(
        session,
        child_id=data.child_id,
        target_type=data.target_type,
        target_id=data.target_id,
        effect=data.effect,
        title=data.title,
        channel_name=data.channel_name,
        note=data.note,
        parent_id=parent.id,
    )
    session.commit()
    events.rules_changed(session, data.child_id)
    return RuleOut.model_validate(rule)


@router.patch("/{rule_id}", response_model=RuleOut)
def update_rule(
    rule_id: int,
    data: RuleUpdate,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> RuleOut:
    rule = _get_rule_or_404(session, rule_id)
    if data.effect is not None:
        rule.effect = data.effect
    if data.note is not None:
        rule.note = data.note
    rule.updated_at = clock.now()
    session.commit()
    events.rules_changed(session, rule.child_id)
    return RuleOut.model_validate(rule)


@router.delete("/{rule_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_rule(
    rule_id: int, _: Parent = Depends(current_parent), session: Session = Depends(get_session)
) -> Response:
    rule = _get_rule_or_404(session, rule_id)
    child_id = rule.child_id
    session.delete(rule)
    session.commit()
    events.rules_changed(session, child_id)
    return Response(status_code=status.HTTP_204_NO_CONTENT)
