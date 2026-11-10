"""Gemeinsame Test-Einrichtung.

Die Umgebung muss stehen, bevor app.config zum ersten Mal gelesen wird — deshalb ganz
oben, vor allen App-Importen.
"""

from __future__ import annotations

import os

os.environ["DATABASE_URL"] = "sqlite://"
os.environ["YTS_JOBS_ENABLED"] = "false"
os.environ["YTS_BASE_URL"] = "https://tube.example"
os.environ["NTFY_URL"] = "http://ntfy.test"
os.environ["NTFY_PUBLIC_URL"] = "https://push.example"
os.environ["NTFY_TOKEN"] = "tk_test"
os.environ["SMTP_HOST"] = ""
os.environ["YTS_UPDATES_DIR"] = "/nonexistent"

from datetime import datetime  # noqa: E402

import pytest  # noqa: E402

from app import clock  # noqa: E402
from app.db import Base, SessionLocal, engine  # noqa: E402
from app.models import Child, Parent  # noqa: E402
from app.security import hash_password, new_topic  # noqa: E402

START = datetime(2026, 11, 8, 12, 0, 0)


class FakeClock:
    def __init__(self) -> None:
        self.value = START

    def advance(self, **delta) -> None:
        from datetime import timedelta

        self.value = self.value + timedelta(**delta)


@pytest.fixture(autouse=True)
def frozen_clock(monkeypatch):
    fake = FakeClock()
    monkeypatch.setattr(clock, "now", lambda: fake.value)
    return fake


@pytest.fixture(autouse=True)
def fresh_database():
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    yield
    Base.metadata.drop_all(engine)


@pytest.fixture
def db():
    session = SessionLocal()
    try:
        yield session
    finally:
        session.close()


def make_parent(db, name="Jessica", email="jessica@example.org", password="geheim123"):
    parent = Parent(
        name=name,
        email=email,
        password_hash=hash_password(password),
        ntfy_topic=new_topic(),
        mail_new_request=True,
        mail_alerts=True,
        created_at=clock.now(),
    )
    db.add(parent)
    db.commit()
    return parent


def make_child(db, name="Mia"):
    child = Child(
        name=name,
        display_mode="compact",
        shorts_enabled=False,
        reward_until=None,
        reward_includes_blocked=False,
        reward_expiry_notified=True,
        ntfy_topic=new_topic(),
        created_at=clock.now(),
    )
    db.add(child)
    db.commit()
    return child


@pytest.fixture
def client():
    from fastapi.testclient import TestClient

    from app.main import app

    return TestClient(app)


@pytest.fixture
def parent(db):
    return make_parent(db)


def login(client, email="jessica@example.org", password="geheim123") -> dict:
    response = client.post("/api/v1/parent/login", json={"email": email, "password": password})
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['token']}"}


@pytest.fixture
def parent_headers(client, parent):
    return login(client)
