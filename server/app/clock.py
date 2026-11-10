"""Zeit an einer Stelle.

Die Datenbank speichert naive UTC-Zeitpunkte. Alle Module rufen clock.now() über das
Modul auf (nicht per from-Import), damit Tests die Uhr austauschen können.
"""

from __future__ import annotations

from datetime import datetime, timezone


def now() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


def to_naive_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value
    return value.astimezone(timezone.utc).replace(tzinfo=None)
