from __future__ import annotations

from tests.conftest import create_child

RULES = "/api/v1/parent/rules"


def rule(child_id, target_id="UC1", effect="allow", target_type="channel", **extra):
    return {"child_id": child_id, "target_type": target_type, "target_id": target_id,
            "effect": effect, **extra}


def test_create_rule_and_kid_sees_it(client, parent_headers, child, kid_headers, pushes):
    response = client.post(RULES, headers=parent_headers,
                           json=rule(child["id"], channel_name="Kanal Eins"))
    assert response.status_code == 201
    assert response.json()["channel_name"] == "Kanal Eins"
    assert any("sync" in p["tags"] for p in pushes)

    state = client.get("/api/v1/kid/state", headers=kid_headers).json()
    assert state["rules"] == [
        {"child_id": child["id"], "target_type": "channel", "target_id": "UC1", "effect": "allow"}
    ]
    assert state["server_time"] == "2026-11-08T12:00:00Z"


def test_upsert_same_target_updates_instead_of_duplicating(client, parent_headers, child):
    first = client.post(RULES, headers=parent_headers, json=rule(child["id"])).json()
    second = client.post(RULES, headers=parent_headers, json=rule(child["id"], effect="block"))
    assert second.status_code == 201
    assert second.json()["id"] == first["id"]
    assert second.json()["effect"] == "block"
    assert len(client.get(RULES, headers=parent_headers).json()) == 1


def test_global_rule_upsert(client, parent_headers):
    client.post(RULES, headers=parent_headers, json=rule(None))
    client.post(RULES, headers=parent_headers, json=rule(None, effect="block"))
    rules = client.get(RULES, headers=parent_headers).json()
    assert len(rules) == 1 and rules[0]["effect"] == "block"


def test_kid_sees_own_and_global_but_not_siblings(client, parent_headers, child, kid_headers):
    sibling = create_child(client, parent_headers, "Antonio")
    client.post(RULES, headers=parent_headers, json=rule(child["id"], "UCmine"))
    client.post(RULES, headers=parent_headers, json=rule(None, "UCall"))
    client.post(RULES, headers=parent_headers, json=rule(sibling["id"], "UCsibling"))
    ids = {r["target_id"] for r in client.get("/api/v1/kid/state", headers=kid_headers).json()["rules"]}
    assert ids == {"UCmine", "UCall"}


def test_filter_by_child(client, parent_headers, child):
    client.post(RULES, headers=parent_headers, json=rule(child["id"], "UCmine"))
    client.post(RULES, headers=parent_headers, json=rule(None, "UCall"))
    only_child = client.get(RULES, headers=parent_headers, params={"child_id": child["id"]}).json()
    assert {r["target_id"] for r in only_child} == {"UCmine", "UCall"}
    only_global = client.get(RULES, headers=parent_headers, params={"global_only": True}).json()
    assert [r["target_id"] for r in only_global] == ["UCall"]


def test_patch_and_delete(client, parent_headers, child):
    created = client.post(RULES, headers=parent_headers, json=rule(child["id"])).json()
    patched = client.patch(f"{RULES}/{created['id']}", headers=parent_headers,
                           json={"effect": "block", "note": "zu viel Geschrei"}).json()
    assert patched["effect"] == "block" and patched["note"] == "zu viel Geschrei"
    assert client.delete(f"{RULES}/{created['id']}", headers=parent_headers).status_code == 204
    assert client.get(RULES, headers=parent_headers).json() == []


def test_rule_for_unknown_child_404(client, parent_headers):
    assert client.post(RULES, headers=parent_headers, json=rule(999)).status_code == 404
