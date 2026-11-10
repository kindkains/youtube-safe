from __future__ import annotations

from datetime import datetime, timedelta, timezone

from app import clock
from app.security import (
    PAIRING_ALPHABET,
    hash_password,
    hash_token,
    new_pairing_code,
    new_token,
    new_topic,
    verify_password,
)


def test_password_roundtrip():
    stored = hash_password("geheim123")
    assert verify_password("geheim123", stored)
    assert not verify_password("falsch", stored)


def test_long_password_is_not_truncated():
    base = "x" * 80
    stored = hash_password(base + "A")
    assert not verify_password(base + "B", stored)


def test_tokens_are_random_and_hash_is_stable():
    a, b = new_token(), new_token()
    assert a != b and len(a) >= 40
    assert hash_token(a) == hash_token(a)
    assert len(hash_token(a)) == 64


def test_pairing_code_shape():
    code = new_pairing_code()
    assert len(code) == 8
    assert set(code) <= set(PAIRING_ALPHABET)


def test_topic_shape():
    topic = new_topic()
    assert topic.startswith("yts-") and len(topic) == 36


def test_to_naive_utc():
    aware = datetime(2026, 1, 1, 13, 0, tzinfo=timezone(timedelta(hours=1)))
    assert clock.to_naive_utc(aware) == datetime(2026, 1, 1, 12, 0)
    naive = datetime(2026, 1, 1, 12, 0)
    assert clock.to_naive_utc(naive) is naive


def test_now_is_naive_utc():
    assert clock.now().tzinfo is None
