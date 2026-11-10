from __future__ import annotations

WATCH = "/api/v1/kid/watch"


def entry(client_id, video_id, channel_id, started_at, seconds, channel_name="Kanal"):
    return {"client_id": client_id, "video_id": video_id, "channel_id": channel_id,
            "title": f"Video {video_id}", "channel_name": channel_name,
            "started_at": started_at, "played_seconds": seconds}


def test_history_newest_first_with_current_status(client, parent_headers, child, kid_headers):
    client.post(WATCH, headers=kid_headers, json={"entries": [
        entry("s-000001", "v1", "UC1", "2026-11-08T10:00:00Z", 100),
        entry("s-000002", "v2", "UC2", "2026-11-08T11:00:00Z", 200),
    ]})
    client.post("/api/v1/parent/rules", headers=parent_headers,
                json={"child_id": child["id"], "target_type": "channel", "target_id": "UC2",
                      "effect": "allow"})
    history = client.get(f"/api/v1/parent/children/{child['id']}/history",
                         headers=parent_headers).json()
    assert [h["video_id"] for h in history] == ["v2", "v1"]
    assert history[0]["allowed_now"] is True and history[0]["reason_now"] == "channel_allow"
    assert history[1]["allowed_now"] is False and history[1]["reason_now"] == "default"


def test_history_paging(client, parent_headers, child, kid_headers):
    client.post(WATCH, headers=kid_headers, json={"entries": [
        entry(f"s-00000{i}", f"v{i}", "UC1", f"2026-11-08T0{i}:00:00Z", 10) for i in range(1, 6)
    ]})
    first = client.get(f"/api/v1/parent/children/{child['id']}/history",
                       headers=parent_headers, params={"limit": 2}).json()
    assert [h["video_id"] for h in first] == ["v5", "v4"]
    rest = client.get(f"/api/v1/parent/children/{child['id']}/history", headers=parent_headers,
                      params={"limit": 10, "before": first[-1]["started_at"]}).json()
    assert [h["video_id"] for h in rest] == ["v3", "v2", "v1"]


def test_stats_by_local_day_and_channel(client, parent_headers, child, kid_headers):
    # 23:30 UTC am 07.11. ist 00:30 am 08.11. in Berlin
    client.post(WATCH, headers=kid_headers, json={"entries": [
        entry("s-000001", "v1", "UC1", "2026-11-07T23:30:00Z", 600, "Kanal Eins"),
        entry("s-000002", "v2", "UC2", "2026-11-08T09:00:00Z", 300, "Kanal Zwei"),
        entry("s-000003", "v3", "UC1", "2026-11-06T09:00:00Z", 120, "Kanal Eins"),
    ]})
    stats = client.get(f"/api/v1/parent/children/{child['id']}/stats",
                       headers=parent_headers, params={"days": 3}).json()
    assert stats["days"] == [
        {"date": "2026-11-06", "seconds": 120, "reward_seconds": 0},
        {"date": "2026-11-07", "seconds": 0, "reward_seconds": 0},
        {"date": "2026-11-08", "seconds": 900, "reward_seconds": 0},
    ]
    assert stats["total_seconds"] == 1020
    assert stats["top_channels"][0] == {"channel_id": "UC1", "channel_name": "Kanal Eins",
                                        "seconds": 720}
