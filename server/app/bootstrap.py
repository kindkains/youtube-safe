"""Erster Elternteil aus der .env — nur, solange noch keiner existiert. Idempotent."""

from __future__ import annotations

import logging
import sys

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import SessionLocal
from app.models import Parent
from app.routers.parent_admin import create_parent

log = logging.getLogger(__name__)


def ensure_first_parent(session: Session) -> None:
    if session.scalar(select(Parent.id).limit(1)) is not None:
        return
    settings = get_settings()
    if not (settings.bootstrap_parent_email and settings.bootstrap_parent_password):
        log.warning("Noch kein Elternteil und YTS_PARENT_EMAIL/YTS_PARENT_PASSWORD nicht gesetzt.")
        return
    create_parent(
        session,
        settings.bootstrap_parent_name or "Eltern",
        settings.bootstrap_parent_email,
        settings.bootstrap_parent_password,
    )
    log.info("Erster Elternteil angelegt: %s", settings.bootstrap_parent_email)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    with SessionLocal() as session:
        ensure_first_parent(session)
    return 0


if __name__ == "__main__":
    sys.exit(main())
