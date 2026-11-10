from __future__ import annotations


def reward_url(child):
    return f"/api/v1/parent/children/{child['id']}/reward"


def test_start_with_minutes(client, parent_headers, child, kid_headers, pushes):
    response = client.post(reward_url(child), headers=parent_headers,
                           json={"minutes": 90, "include_blocked": True})
    assert response.status_code == 200
    body = response.json()
    assert body["reward_active"] is True
    assert body["reward_until"] == "2026-11-08T13:30:00Z"
    assert body["reward_includes_blocked"] is True
    state = client.get("/api/v1/kid/state", headers=kid_headers).json()
    assert state["child"]["reward_active"] is True
    assert any(p["title"] == "Belohnungsmodus aktiv 🎉" for p in pushes)


def test_start_with_until(client, parent_headers, child):
    body = client.post(reward_url(child), headers=parent_headers,
                       json={"until": "2026-11-08T19:00:00+01:00"}).json()
    assert body["reward_until"] == "2026-11-08T18:00:00Z"
    assert body["reward_includes_blocked"] is False


def test_reward_must_be_limited(client, parent_headers, child):
    assert client.post(reward_url(child), headers=parent_headers, json={}).status_code == 422
    assert client.post(reward_url(child), headers=parent_headers,
                       json={"minutes": 5, "until": "2026-11-09T00:00:00Z"}).status_code == 422
    assert client.post(reward_url(child), headers=parent_headers,
                       json={"until": "2026-11-08T11:00:00Z"}).status_code == 400
    assert client.post(reward_url(child), headers=parent_headers,
                       json={"until": "2026-12-31T00:00:00Z"}).status_code == 400


def test_reward_expires_by_time(client, parent_headers, child, kid_headers, frozen_clock):
    client.post(reward_url(child), headers=parent_headers, json={"minutes": 30})
    frozen_clock.advance(minutes=31)
    state = client.get("/api/v1/kid/state", headers=kid_headers).json()
    assert state["child"]["reward_active"] is False


def test_stop_now(client, parent_headers, child, kid_headers, pushes):
    client.post(reward_url(child), headers=parent_headers, json={"minutes": 30, "include_blocked": True})
    response = client.delete(reward_url(child), headers=parent_headers)
    assert response.status_code == 200
    assert response.json()["reward_active"] is False
    assert response.json()["reward_includes_blocked"] is False
    assert any(p["title"] == "Belohnungsmodus beendet" for p in pushes)
