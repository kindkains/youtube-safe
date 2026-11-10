from __future__ import annotations

from tests.conftest import login


def test_login_ok_returns_token_and_parent(client, parent):
    response = client.post(
        "/api/v1/parent/login", json={"email": "Jessica@Example.org ", "password": "geheim123"}
    )
    assert response.status_code == 200
    body = response.json()
    assert body["token"]
    assert body["parent"]["name"] == "Jessica"
    assert "password_hash" not in body["parent"]


def test_login_wrong_password(client, parent):
    response = client.post(
        "/api/v1/parent/login", json={"email": "jessica@example.org", "password": "nein"}
    )
    assert response.status_code == 401


def test_me_requires_token(client, parent):
    assert client.get("/api/v1/parent/me").status_code == 401
    assert (
        client.get("/api/v1/parent/me", headers={"Authorization": "Bearer quatsch"}).status_code
        == 401
    )


def test_me_and_logout(client, parent):
    headers = login(client)
    assert client.get("/api/v1/parent/me", headers=headers).json()["email"] == "jessica@example.org"
    assert client.post("/api/v1/parent/logout", headers=headers).status_code == 204
    assert client.get("/api/v1/parent/me", headers=headers).status_code == 401


def test_health(client):
    assert client.get("/health").json() == {"status": "ok"}
