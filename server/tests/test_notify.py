from __future__ import annotations

import httpx
from sqlalchemy import select

from app import notify
from app.models import MailOutbox


def test_queue_mail_noop_without_smtp(db):
    notify.queue_mail(db, "a@example.org", "Betreff", "Text")
    db.commit()
    assert db.scalars(select(MailOutbox)).all() == []


def test_outbox_sends_and_retries(db, monkeypatch, frozen_clock):
    monkeypatch.setattr(notify, "mail_enabled", lambda: True)
    calls: list[str] = []
    outcomes = [RuntimeError("SMTP weg"), RuntimeError("SMTP weg"), None]

    def fake_smtp(to, subject, body):
        calls.append(to)
        outcome = outcomes.pop(0)
        if outcome:
            raise outcome

    monkeypatch.setattr(notify, "_smtp_send", fake_smtp)
    notify.queue_mail(db, "a@example.org", "Betreff", "Text")
    db.commit()

    assert notify.process_mail_outbox(db) == 0  # 1. Versuch scheitert
    mail = db.scalar(select(MailOutbox))
    assert mail.attempts == 1 and "SMTP weg" in mail.last_error

    assert notify.process_mail_outbox(db) == 0  # noch nicht fällig
    assert len(calls) == 1

    frozen_clock.advance(minutes=5)
    assert notify.process_mail_outbox(db) == 0  # 2. Versuch scheitert
    frozen_clock.advance(minutes=15)
    assert notify.process_mail_outbox(db) == 1  # 3. Versuch klappt
    assert db.scalar(select(MailOutbox)).sent_at is not None


def test_outbox_gives_up_after_four_attempts(db, monkeypatch, frozen_clock):
    monkeypatch.setattr(notify, "mail_enabled", lambda: True)

    def always_fail(to, subject, body):
        raise RuntimeError("kaputt")

    monkeypatch.setattr(notify, "_smtp_send", always_fail)
    notify.queue_mail(db, "a@example.org", "Betreff", "Text")
    db.commit()
    for minutes in (0, 5, 15, 60):
        frozen_clock.advance(minutes=minutes)
        notify.process_mail_outbox(db)
    mail = db.scalar(select(MailOutbox))
    assert mail.failed is True and mail.attempts == 4


def test_real_send_push_posts_json(monkeypatch):
    # Die autouse-Fixture hat send_push ersetzt; hier wird die echte Funktion geprüft.
    captured = {}

    def fake_post(url, json, headers, timeout):
        captured.update(url=url, json=json, headers=headers)
        return httpx.Response(200, request=httpx.Request("POST", url))

    monkeypatch.setattr(notify.httpx, "post", fake_post)
    assert notify._send_push_real("yts-abc", "Titel ä", "Text", tags=("sync",)) is True
    assert captured["url"] == "http://ntfy.test"
    assert captured["json"]["topic"] == "yts-abc"
    assert captured["json"]["title"] == "Titel ä"
    assert captured["json"]["tags"] == ["sync"]
    assert captured["headers"]["Authorization"] == "Bearer tk_test"


def test_real_send_push_swallows_errors(monkeypatch):
    def broken_post(*args, **kwargs):
        raise httpx.ConnectError("weg")

    monkeypatch.setattr(notify.httpx, "post", broken_post)
    assert notify._send_push_real("yts-abc", "T", "M") is False
