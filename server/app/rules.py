"""Regeln lesen und schreiben — von mehreren Routern geteilt."""

from __future__ import annotations

from sqlalchemy import or_, select
from sqlalchemy.orm import Session

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
