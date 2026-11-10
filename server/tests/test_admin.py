from __future__ import annotations

from sqlalchemy import select

from app import bootstrap
from app.config import get_settings
from app.models import Parent
from tests.conftest import login


def test_create_parent_and_login(client, parent_headers):
    response = client.post(
        "/api/v1/parent/parents",
        headers=parent_headers,
        json={"name": "Anna", "email": "Anna@Example.org", "password": "annapass1"},
    )
    assert response.status_code == 201
    assert response.json()["email"] == "anna@example.org"
    login(client, "anna@example.org", "annapass1")


def test_duplicate_email_rejected(client, parent_headers):
    response = client.post(
        "/api/v1/parent/parents",
        headers=parent_headers,
        json={"name": "Doppelt", "email": "jessica@example.org", "password": "xxxxxxxx"},
    )
    assert response.status_code == 409


def test_list_parents(client, parent_headers):
    names = [p["name"] for p in client.get("/api/v1/parent/parents", headers=parent_headers).json()]
    assert names == ["Jessica"]


def test_update_me(client, parent_headers):
    response = client.patch(
        "/api/v1/parent/me",
        headers=parent_headers,
        json={"mail_new_request": False, "password": "neuespasswort"},
    )
    assert response.status_code == 200
    assert response.json()["mail_new_request"] is False
    login(client, password="neuespasswort")


def test_push_info(client, parent_headers):
    body = client.get("/api/v1/parent/push", headers=parent_headers).json()
    assert body["ntfy_url"] == "https://push.example"
    assert body["ntfy_topic"].startswith("yts-")


def test_bootstrap_creates_first_parent_once(db, monkeypatch):
    settings = get_settings()
    monkeypatch.setattr(settings, "bootstrap_parent_name", "Jessica")
    monkeypatch.setattr(settings, "bootstrap_parent_email", "Jessica@Example.org")
    monkeypatch.setattr(settings, "bootstrap_parent_password", "startpass1")
    bootstrap.ensure_first_parent(db)
    bootstrap.ensure_first_parent(db)
    parents = db.scalars(select(Parent)).all()
    assert [p.email for p in parents] == ["jessica@example.org"]


def test_bootstrap_without_settings_does_nothing(db):
    bootstrap.ensure_first_parent(db)
    assert db.scalars(select(Parent)).all() == []
