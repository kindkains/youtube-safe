from __future__ import annotations

from app import jobs
from app.db import SessionLocal


def run():
    with SessionLocal() as session:
        jobs.run_periodic(session)


def test_reward_expiry_notified_once(client, parent_headers, child, pushes, frozen_clock):
    client.post(f"/api/v1/parent/children/{child['id']}/reward", headers=parent_headers,
                json={"minutes": 30})
    run()
    assert not [p for p in pushes if p["title"] == "Belohnungsmodus abgelaufen"]
    frozen_clock.advance(minutes=31)
    run()
    run()
    assert len([p for p in pushes if p["title"] == "Belohnungsmodus abgelaufen"]) == 1


def test_stopped_reward_does_not_report_expiry(client, parent_headers, child, pushes, frozen_clock):
    url = f"/api/v1/parent/children/{child['id']}/reward"
    client.post(url, headers=parent_headers, json={"minutes": 30})
    client.delete(url, headers=parent_headers)
    frozen_clock.advance(hours=1)
    run()
    assert not [p for p in pushes if p["title"] == "Belohnungsmodus abgelaufen"]


def test_stale_device_alert_once_and_reset_on_contact(client, kid_headers, pushes, frozen_clock):
    def stale_alerts():
        return [p for p in pushes if p["title"] == "Gerät meldet sich nicht mehr"]

    frozen_clock.advance(hours=23)
    run()
    assert stale_alerts() == []
    frozen_clock.advance(hours=2)
    run()
    run()
    assert len(stale_alerts()) == 1

    client.get("/api/v1/kid/state", headers=kid_headers)  # Gerät meldet sich wieder
    frozen_clock.advance(hours=25)
    run()
    assert len(stale_alerts()) == 2


def test_unpaired_device_never_alerts(client, parent_headers, child, kid_headers, pushes, frozen_clock):
    device = client.get(f"/api/v1/parent/children/{child['id']}/devices",
                        headers=parent_headers).json()[0]
    client.delete(f"/api/v1/parent/devices/{device['id']}", headers=parent_headers)
    frozen_clock.advance(days=3)
    run()
    assert not [p for p in pushes if p["title"] == "Gerät meldet sich nicht mehr"]
