from __future__ import annotations

from tests.conftest import create_child, make_parent, login

KID_REQ = "/api/v1/kid/requests"
PARENT_REQ = "/api/v1/parent/requests"


def video_request(**extra):
    return {"target_type": "video", "target_id": "vid1", "title": "Cooles Video",
            "channel_id": "UC1", "channel_name": "Kanal Eins",
            "thumbnail_url": "https://i.ytimg.com/vi/vid1/hqdefault.jpg", **extra}


def channel_request(**extra):
    return {"target_type": "channel", "target_id": "UC1", "title": "Kanal Eins",
            "channel_id": "UC1", "channel_name": "Kanal Eins",
            "source_video_id": "vid1", "source_video_title": "Cooles Video", **extra}


def test_kid_creates_request_parents_get_push(client, parent_headers, child, kid_headers, pushes):
    response = client.post(KID_REQ, headers=kid_headers, json=video_request(message="Für Bio!"))
    assert response.status_code == 201
    body = response.json()
    assert body["url"] == "https://www.youtube.com/watch?v=vid1"
    assert body["status"] == "open"
    parent_push = [p for p in pushes if any(t.startswith("request:") for t in p["tags"])]
    assert len(parent_push) == 1
    assert "Mia" in parent_push[0]["title"] and "Für Bio!" in parent_push[0]["message"]


def test_duplicate_open_request_is_reused(client, child, kid_headers, pushes):
    first = client.post(KID_REQ, headers=kid_headers, json=video_request()).json()
    second = client.post(KID_REQ, headers=kid_headers, json=video_request())
    assert second.status_code == 201 and second.json()["id"] == first["id"]
    assert len([p for p in pushes if any(t.startswith("request:") for t in p["tags"])]) == 1


def test_channel_url(client, child, kid_headers):
    body = client.post(KID_REQ, headers=kid_headers, json=channel_request()).json()
    assert body["url"] == "https://www.youtube.com/channel/UC1"


def test_approve_for_child_creates_rule_and_notifies(client, parent_headers, child, kid_headers, pushes):
    req = client.post(KID_REQ, headers=kid_headers, json=video_request()).json()
    response = client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers,
                           json={"action": "approve"})
    assert response.status_code == 200
    assert response.json()["status"] == "approved"
    assert response.json()["decided_by_name"] == "Jessica"
    rules = client.get("/api/v1/kid/state", headers=kid_headers).json()["rules"]
    assert rules == [{"child_id": child["id"], "target_type": "video", "target_id": "vid1",
                      "effect": "allow"}]
    assert any(p["title"] == "Freigegeben 🎉" for p in pushes)


def test_approve_for_all_creates_global_rule(client, parent_headers, child, kid_headers):
    req = client.post(KID_REQ, headers=kid_headers, json=channel_request()).json()
    client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers,
                json={"action": "approve", "scope": "all"})
    rules = client.get("/api/v1/parent/rules", headers=parent_headers).json()
    assert rules[0]["child_id"] is None and rules[0]["target_type"] == "channel"


def test_approve_video_only_from_channel_request(client, parent_headers, child, kid_headers):
    req = client.post(KID_REQ, headers=kid_headers, json=channel_request()).json()
    client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers,
                json={"action": "approve_video_only"})
    rules = client.get("/api/v1/parent/rules", headers=parent_headers).json()
    assert [(r["target_type"], r["target_id"], r["title"]) for r in rules] == [
        ("video", "vid1", "Cooles Video")
    ]


def test_approve_video_only_needs_source_video(client, parent_headers, child, kid_headers):
    req = client.post(KID_REQ, headers=kid_headers,
                      json=channel_request(source_video_id="", source_video_title="")).json()
    response = client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers,
                           json={"action": "approve_video_only"})
    assert response.status_code == 400


def test_reject_with_reason(client, parent_headers, child, kid_headers, pushes):
    req = client.post(KID_REQ, headers=kid_headers, json=video_request()).json()
    body = client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers,
                       json={"action": "reject", "reason": "Zu gruselig"}).json()
    assert body["status"] == "rejected" and body["reason"] == "Zu gruselig"
    kid_push = [p for p in pushes if p["title"] == "Leider abgelehnt"]
    assert "Zu gruselig" in kid_push[0]["message"]
    own = client.get(KID_REQ, headers=kid_headers).json()
    assert own[0]["reason"] == "Zu gruselig"


def test_reject_and_block_creates_block_rule(client, parent_headers, child, kid_headers):
    req = client.post(KID_REQ, headers=kid_headers, json=channel_request()).json()
    client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers,
                json={"action": "reject_and_block"})
    rules = client.get("/api/v1/parent/rules", headers=parent_headers).json()
    assert rules[0]["effect"] == "block" and rules[0]["target_id"] == "UC1"


def test_reject_after_approve_removes_allow_rule(client, parent_headers, child, kid_headers):
    req = client.post(KID_REQ, headers=kid_headers, json=video_request()).json()
    client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers, json={"action": "approve"})
    client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers, json={"action": "reject"})
    assert client.get("/api/v1/parent/rules", headers=parent_headers).json() == []


def test_other_parent_gets_info(client, db, parent_headers, child, kid_headers, pushes):
    anna = make_parent(db, name="Anna", email="anna@example.org")
    req = client.post(KID_REQ, headers=kid_headers, json=video_request()).json()
    client.post(f"{PARENT_REQ}/{req['id']}/decision", headers=parent_headers, json={"action": "approve"})
    info = [p for p in pushes if p["topic"] == anna.ntfy_topic and "info" in p["tags"]]
    assert info and info[0]["title"] == "Jessica hat freigegeben"


def test_list_filters(client, parent_headers, child, kid_headers, frozen_clock):
    sibling = create_child(client, parent_headers, "Antonio")
    from tests.conftest import pair_kid
    sibling_headers = pair_kid(client, parent_headers, sibling["id"])
    a = client.post(KID_REQ, headers=kid_headers, json=video_request()).json()
    frozen_clock.advance(hours=1)
    client.post(KID_REQ, headers=sibling_headers, json=video_request(target_id="vid2"))
    client.post(f"{PARENT_REQ}/{a['id']}/decision", headers=parent_headers, json={"action": "approve"})

    all_requests = client.get(PARENT_REQ, headers=parent_headers).json()
    assert [r["target_id"] for r in all_requests] == ["vid2", "vid1"]  # neueste zuerst
    open_only = client.get(PARENT_REQ, headers=parent_headers, params={"status": "open"}).json()
    assert [r["child_name"] for r in open_only] == ["Antonio"]
    mia_only = client.get(PARENT_REQ, headers=parent_headers, params={"child_id": child["id"]}).json()
    assert [r["target_id"] for r in mia_only] == ["vid1"]
    since = client.get(PARENT_REQ, headers=parent_headers,
                       params={"since": "2026-11-08T12:30:00Z"}).json()
    assert [r["target_id"] for r in since] == ["vid2"]


def test_kid_cannot_see_siblings_requests(client, parent_headers, child, kid_headers):
    sibling = create_child(client, parent_headers, "Antonio")
    from tests.conftest import pair_kid
    sibling_headers = pair_kid(client, parent_headers, sibling["id"])
    client.post(KID_REQ, headers=sibling_headers, json=video_request())
    assert client.get(KID_REQ, headers=kid_headers).json() == []
