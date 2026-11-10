from __future__ import annotations

import json

from tests.conftest import create_child, pair_kid


def test_create_and_list_children(client, parent_headers):
    create_child(client, parent_headers, "Mia")
    create_child(client, parent_headers, "Antonio")
    children = client.get("/api/v1/parent/children", headers=parent_headers).json()
    assert [c["name"] for c in children] == ["Mia", "Antonio"]
    assert children[0]["display_mode"] == "compact"
    assert children[0]["shorts_enabled"] is False
    assert children[0]["reward_active"] is False


def test_update_child_settings_syncs_kid(client, parent_headers, child, pushes):
    response = client.patch(
        f"/api/v1/parent/children/{child['id']}",
        headers=parent_headers,
        json={"display_mode": "full", "shorts_enabled": True},
    )
    assert response.status_code == 200
    assert response.json()["display_mode"] == "full"
    assert any("sync" in p["tags"] for p in pushes)


def test_pairing_code_payload(client, parent_headers, child):
    body = client.post(
        f"/api/v1/parent/children/{child['id']}/pairing-code", headers=parent_headers
    ).json()
    assert len(body["code"]) == 8
    payload = json.loads(body["qr_payload"])
    assert payload == {"server": "https://tube.example", "code": body["code"]}


def test_pair_returns_push_info_and_notifies(client, parent_headers, child, pushes):
    code = client.post(
        f"/api/v1/parent/children/{child['id']}/pairing-code", headers=parent_headers
    ).json()["code"]
    body = client.post("/api/v1/kid/pair", json={"code": code.lower(), "app_version": "1.2"}).json()
    assert body["child_name"] == "Mia"
    assert body["ntfy_url"] == "https://push.example"
    assert body["ntfy_topic"].startswith("yts-")
    assert any(p["title"] == "Neues Gerät gekoppelt" for p in pushes)


def test_pairing_code_single_use_and_expiry(client, parent_headers, child, frozen_clock):
    code = client.post(
        f"/api/v1/parent/children/{child['id']}/pairing-code", headers=parent_headers
    ).json()["code"]
    assert client.post("/api/v1/kid/pair", json={"code": code}).status_code == 200
    assert client.post("/api/v1/kid/pair", json={"code": code}).status_code == 400

    code2 = client.post(
        f"/api/v1/parent/children/{child['id']}/pairing-code", headers=parent_headers
    ).json()["code"]
    frozen_clock.advance(minutes=11)
    assert client.post("/api/v1/kid/pair", json={"code": code2}).status_code == 400


def test_devices_and_unpair(client, parent_headers, child, kid_headers):
    devices = client.get(
        f"/api/v1/parent/children/{child['id']}/devices", headers=parent_headers
    ).json()
    assert len(devices) == 1 and devices[0]["app_version"] == "1.0.0"
    assert client.get("/api/v1/kid/state", headers=kid_headers).status_code == 200

    response = client.delete(f"/api/v1/parent/devices/{devices[0]['id']}", headers=parent_headers)
    assert response.status_code == 204
    assert client.get("/api/v1/kid/state", headers=kid_headers).status_code == 401


def test_kid_token_cannot_use_parent_api(client, kid_headers):
    assert client.get("/api/v1/parent/children", headers=kid_headers).status_code == 401


def test_parent_token_cannot_use_kid_api(client, parent_headers):
    assert client.get("/api/v1/kid/state", headers=parent_headers).status_code == 401


def test_unknown_child_404(client, parent_headers):
    assert client.patch(
        "/api/v1/parent/children/999", headers=parent_headers, json={"name": "X"}
    ).status_code == 404
