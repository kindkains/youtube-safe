"""Entscheidungslogik: darf ein Kind ein Video sehen?

Reine Funktion ohne Datenbank. Dieselbe Logik steckt in der App (Modul core); beide
Seiten werden gegen shared/gate-cases.json getestet, damit sie nie auseinanderlaufen.

Reihenfolge (erste zutreffende Stufe gewinnt):
1. Belohnungsmodus mit „auch Gesperrtes“   -> erlaubt
2./3. Video-Regel (block / allow)
4./5. Kanal-Regel (block / allow)
6. Belohnungsmodus                          -> erlaubt
7. sonst                                    -> gesperrt
Innerhalb einer Stufe schlägt die Regel des Kindes die Regel für alle Kinder.
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass


@dataclass(frozen=True)
class RuleView:
    child_id: int | None
    target_type: str
    target_id: str
    effect: str


@dataclass(frozen=True)
class Decision:
    allowed: bool
    reason: str


def _effective_effect(
    rules: Iterable[RuleView], child_id: int, target_type: str, target_id: str
) -> str | None:
    specific: RuleView | None = None
    general: RuleView | None = None
    for rule in rules:
        if rule.target_type != target_type or rule.target_id != target_id:
            continue
        if rule.child_id is None:
            general = rule
        elif rule.child_id == child_id:
            specific = rule
    chosen = specific or general
    return chosen.effect if chosen else None


def decide(
    rules: Iterable[RuleView],
    *,
    child_id: int,
    video_id: str,
    channel_id: str,
    reward_active: bool,
    reward_includes_blocked: bool,
) -> Decision:
    rules = list(rules)
    if reward_active and reward_includes_blocked:
        return Decision(True, "reward_all")

    video = _effective_effect(rules, child_id, "video", video_id)
    if video == "block":
        return Decision(False, "video_block")
    if video == "allow":
        return Decision(True, "video_allow")

    channel = _effective_effect(rules, child_id, "channel", channel_id)
    if channel == "block":
        return Decision(False, "channel_block")
    if channel == "allow":
        return Decision(True, "channel_allow")

    if reward_active:
        return Decision(True, "reward")
    return Decision(False, "default")
