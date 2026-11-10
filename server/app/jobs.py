"""Periodische Aufgaben: Mail-Outbox, Ende des Belohnungsmodus, stumme Geräte."""

from __future__ import annotations

import asyncio
import logging
from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock, events, notify
from app.db import SessionLocal
from app.models import Child, Device

log = logging.getLogger(__name__)

INTERVAL_SECONDS = 60
STALE_AFTER = timedelta(hours=24)


def run_periodic(session: Session) -> None:
    now = clock.now()
    notify.process_mail_outbox(session)

    expired = session.scalars(
        select(Child).where(
            Child.reward_until.is_not(None),
            Child.reward_until <= now,
            Child.reward_expiry_notified.is_(False),
        )
    ).all()
    for child in expired:
        child.reward_expiry_notified = True
        events.reward_expired(session, child)

    stale = session.scalars(
        select(Device).where(
            Device.unpaired_at.is_(None),
            Device.last_seen < now - STALE_AFTER,
            Device.stale_notified_at.is_(None),
        )
    ).all()
    for device in stale:
        device.stale_notified_at = now
        events.device_stale(session, device, session.get(Child, device.child_id))

    session.commit()


def _run_once() -> None:
    with SessionLocal() as session:
        run_periodic(session)


async def periodic_loop() -> None:
    while True:
        try:
            await asyncio.to_thread(_run_once)
        except Exception:  # noqa: BLE001 - ein Fehler darf die Schleife nicht beenden
            log.exception("Periodische Aufgabe fehlgeschlagen")
        await asyncio.sleep(INTERVAL_SECONDS)
