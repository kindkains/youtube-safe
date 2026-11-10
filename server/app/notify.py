"""Transport für Benachrichtigungen: ntfy-Push und Mail-Outbox.

Wer was bekommt, entscheidet app/events.py. Fehler im Transport werden protokolliert,
aber nie hochgereicht — eine Freigabe darf nicht daran scheitern, dass ntfy oder der
Mailserver gerade weg ist.
"""

from __future__ import annotations

import logging
import smtplib
from collections.abc import Sequence
from datetime import timedelta
from email.message import EmailMessage

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock
from app.config import get_settings
from app.models import MailOutbox

log = logging.getLogger(__name__)

# Wartezeiten nach dem 1., 2. und 3. Fehlversuch; nach dem 4. wird aufgegeben.
RETRY_DELAYS = (timedelta(minutes=5), timedelta(minutes=15), timedelta(minutes=60))


# --- Push ------------------------------------------------------------------------


def _send_push_real(
    topic: str, title: str, message: str, *, tags: Sequence[str] = (), priority: int = 3
) -> bool:
    settings = get_settings()
    if not settings.push_enabled or not topic:
        return False
    payload = {
        "topic": topic,
        "title": title,
        "message": message,
        "tags": list(tags),
        "priority": priority,
    }
    headers = {"Authorization": f"Bearer {settings.ntfy_token}"} if settings.ntfy_token else {}
    try:
        response = httpx.post(settings.ntfy_url, json=payload, headers=headers, timeout=10)
        response.raise_for_status()
    except httpx.HTTPError as exc:
        log.warning("Push an %s fehlgeschlagen: %s", topic, exc)
        return False
    return True


# Tests ersetzen send_push; der echte Weg bleibt über _send_push_real prüfbar.
send_push = _send_push_real


# --- Mail ------------------------------------------------------------------------


def mail_enabled() -> bool:
    return get_settings().mail_enabled


def queue_mail(session: Session, to_address: str, subject: str, body: str) -> None:
    """Legt eine Mail in die Outbox. Commit macht der Aufrufer."""
    if not mail_enabled() or not to_address:
        return
    session.add(
        MailOutbox(
            to_address=to_address,
            subject=subject,
            body=body,
            attempts=0,
            next_attempt_at=clock.now(),
            failed=False,
            last_error="",
        )
    )


def _smtp_send(to_address: str, subject: str, body: str) -> None:
    settings = get_settings()
    message = EmailMessage()
    message["Subject"] = subject
    message["From"] = settings.smtp_from or f"youtube-safe@{settings.smtp_host}"
    message["To"] = to_address
    message.set_content(body)
    with smtplib.SMTP(settings.smtp_host, settings.smtp_port, timeout=15) as smtp:
        if settings.smtp_starttls:
            smtp.starttls()
        if settings.smtp_user:
            smtp.login(settings.smtp_user, settings.smtp_password)
        smtp.send_message(message)


def process_mail_outbox(session: Session) -> int:
    """Verschickt alle fälligen Mails. Gibt die Zahl der erfolgreich versandten zurück."""
    if not mail_enabled():
        return 0
    now = clock.now()
    due = session.scalars(
        select(MailOutbox).where(
            MailOutbox.sent_at.is_(None),
            MailOutbox.failed.is_(False),
            MailOutbox.next_attempt_at <= now,
        )
    ).all()
    sent = 0
    for mail in due:
        try:
            _smtp_send(mail.to_address, mail.subject, mail.body)
        except Exception as exc:  # noqa: BLE001 - jeder SMTP-Fehler zählt als Fehlversuch
            mail.attempts += 1
            mail.last_error = str(exc)[:2000]
            if mail.attempts > len(RETRY_DELAYS):
                mail.failed = True
                log.error("Mail an %s endgültig gescheitert: %s", mail.to_address, exc)
            else:
                mail.next_attempt_at = now + RETRY_DELAYS[mail.attempts - 1]
                log.warning("Mail an %s gescheitert (Versuch %d): %s",
                            mail.to_address, mail.attempts, exc)
        else:
            mail.attempts += 1
            mail.sent_at = now
            sent += 1
    session.commit()
    return sent
