from __future__ import annotations

from sqlalchemy import select

from app.models import ContentRequest, Device, Rule, WatchSession
from tests.conftest import START, make_child, make_parent


def test_tables_roundtrip(db):
    parent = make_parent(db)
    child = make_child(db)
    db.add(Rule(child_id=None, target_type="channel", target_id="UC1", effect="allow",
                title="", channel_name="Kanal", note="", created_by=parent.id,
                created_at=START, updated_at=START))
    db.add(Device(child_id=child.id, token_hash="a" * 64, app_version="1.0",
                  paired_at=START, last_seen=START))
    db.add(ContentRequest(child_id=child.id, target_type="video", target_id="v1",
                          title="T", channel_id="UC1", channel_name="K",
                          url="https://www.youtube.com/watch?v=v1", status="open",
                          created_at=START))
    db.add(WatchSession(child_id=child.id, client_id="c" * 16, video_id="v1",
                        channel_id="UC1", started_at=START, played_seconds=10,
                        in_reward_mode=False, updated_at=START))
    db.commit()
    assert db.scalar(select(Rule)).child_id is None
    assert db.scalar(select(Device)).unpaired_at is None
    assert db.scalar(select(ContentRequest)).decided_at is None
    assert db.scalar(select(WatchSession)).played_seconds == 10
