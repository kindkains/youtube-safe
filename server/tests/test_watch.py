from __future__ import annotations

from sqlalchemy import select

from app.models import Child, WatchSession

WATCH = "/api/v1/kid/watch"


def entry(client_id="sess-0001", seconds=30, **extra):
    return {"client_id": client_id, "video_id": "vid1", "channel_id": "UC1",
            "title": "Video", "channel_name": "Kanal", "started_at": "2026-11-08T11:50:00Z",
            "played_seconds": seconds, **extra}


def test_watch_upsert_keeps_maximum(client, db, kid_headers):
    assert client.post(WATCH, headers=kid_headers, json={"entries": [entry(seconds=30)]}).status_code == 204
    client.post(WATCH, headers=kid_headers, json={"entries": [entry(seconds=90)]})
    client.post(WATCH, headers=kid_headers, json={"entries": [entry(seconds=60)]})  # verspätet
    sessions = db.scalars(select(WatchSession)).all()
    assert len(sessions) == 1 and sessions[0].played_seconds == 90


def test_watch_marks_reward_mode(client, db, parent_headers, child, kid_headers):
    client.post(f"/api/v1/parent/children/{child['id']}/reward", headers=parent_headers,
                json={"minutes": 30})
    client.post(WATCH, headers=kid_headers, json={"entries": [entry()]})
    assert db.scalar(select(WatchSession)).in_reward_mode is True


def test_client_id_of_other_child_is_ignored(client, db, parent_headers, kid_headers):
    from tests.conftest import create_child, pair_kid

    sibling = create_child(client, parent_headers, "Antonio")
    sibling_headers = pair_kid(client, parent_headers, sibling["id"])
    client.post(WATCH, headers=kid_headers, json={"entries": [entry(seconds=30)]})
    client.post(WATCH, headers=sibling_headers, json={"entries": [entry(seconds=999)]})
    assert db.scalar(select(WatchSession)).played_seconds == 30


def test_extractor_error_notifies_at_most_every_six_hours(client, kid_headers, pushes, frozen_clock):
    def alerts():
        return [p for p in pushes if "alert" in p["tags"]]

    body = {"kind": "extractor_error", "detail": "ParsingException"}
    assert client.post("/api/v1/kid/events", headers=kid_headers, json=body).status_code == 204
    client.post("/api/v1/kid/events", headers=kid_headers, json=body)
    assert len(alerts()) == 1
    frozen_clock.advance(hours=6, minutes=1)
    client.post("/api/v1/kid/events", headers=kid_headers, json=body)
    assert len(alerts()) == 2
