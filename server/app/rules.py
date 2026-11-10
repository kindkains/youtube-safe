"""Regeln lesen und schreiben — von mehreren Routern geteilt."""

from __future__ import annotations

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app import clock
from app.gate import RuleView
from app.models import Rule


def rules_for_child(session: Session, child_id: int) -> list[Rule]:
    """Alle Regeln, die für das Kind gelten: eigene und die für alle Kinder."""
    return list(
        session.scalars(
            select(Rule)
            .where(or_(Rule.child_id == child_id, Rule.child_id.is_(None)))
            .order_by(Rule.id)
        )
    )


def as_views(rules: list[Rule]) -> list[RuleView]:
    return [RuleView(r.child_id, r.target_type, r.target_id, r.effect) for r in rules]


def find_rule(session: Session, child_id: int | None, target_type: str, target_id: str) -> Rule | None:
    child_clause = Rule.child_id.is_(None) if child_id is None else Rule.child_id == child_id
    return session.scalar(
        select(Rule).where(child_clause, Rule.target_type == target_type, Rule.target_id == target_id)
    )


def upsert_rule(
    session: Session,
    *,
    child_id: int | None,
    target_type: str,
    target_id: str,
    effect: str,
    title: str = "",
    channel_name: str = "",
    note: str | None = None,
    parent_id: int | None = None,
) -> Rule:
    """Legt die Regel an oder ändert die bestehende für dasselbe Ziel. Commit macht der Aufrufer.

    Leere Titel/Kanalnamen überschreiben vorhandene Werte nicht.
    """
    now = clock.now()
    rule = find_rule(session, child_id, target_type, target_id)
    if rule is None:
        rule = Rule(
            child_id=child_id,
            target_type=target_type,
            target_id=target_id,
            title=title,
            channel_name=channel_name,
            effect=effect,
            note=note or "",
            created_by=parent_id,
            created_at=now,
            updated_at=now,
        )
        session.add(rule)
        session.flush()
        return rule
    rule.effect = effect
    if title:
        rule.title = title
    if channel_name:
        rule.channel_name = channel_name
    if note is not None:
        rule.note = note
    rule.updated_at = now
    session.flush()
    return rule
