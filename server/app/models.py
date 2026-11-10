"""Datenmodell.

Zeitpunkte sind naive UTC-Werte. Spalten-Defaults greifen erst beim INSERT — wer ein
Objekt anlegt und vor dem Flush damit rechnet, setzt die Werte selbst (Lehre aus openVerba).
"""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from app import clock
from app.db import Base

TARGET_CHANNEL = "channel"
TARGET_VIDEO = "video"
EFFECT_ALLOW = "allow"
EFFECT_BLOCK = "block"
STATUS_OPEN = "open"
STATUS_APPROVED = "approved"
STATUS_REJECTED = "rejected"
DISPLAY_COMPACT = "compact"
DISPLAY_FULL = "full"


def _now() -> datetime:
    return clock.now()


class Parent(Base):
    __tablename__ = "parent"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str] = mapped_column(String(255), unique=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    ntfy_topic: Mapped[str] = mapped_column(String(64), unique=True)
    mail_new_request: Mapped[bool] = mapped_column(Boolean, default=True)
    mail_alerts: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class ParentToken(Base):
    __tablename__ = "parent_token"

    id: Mapped[int] = mapped_column(primary_key=True)
    parent_id: Mapped[int] = mapped_column(ForeignKey("parent.id", ondelete="CASCADE"))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    last_used_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class Child(Base):
    __tablename__ = "child"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    display_mode: Mapped[str] = mapped_column(String(10), default=DISPLAY_COMPACT)
    shorts_enabled: Mapped[bool] = mapped_column(Boolean, default=False)
    reward_until: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    reward_includes_blocked: Mapped[bool] = mapped_column(Boolean, default=False)
    # True, sobald das Ende des Belohnungsmodus gemeldet wurde (oder keiner läuft).
    reward_expiry_notified: Mapped[bool] = mapped_column(Boolean, default=True)
    ntfy_topic: Mapped[str] = mapped_column(String(64), unique=True)
    disturbance_notified_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)

    def reward_active(self, now: datetime) -> bool:
        return self.reward_until is not None and self.reward_until > now


class Device(Base):
    __tablename__ = "device"

    id: Mapped[int] = mapped_column(primary_key=True)
    child_id: Mapped[int] = mapped_column(ForeignKey("child.id", ondelete="CASCADE"))
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    app_version: Mapped[str] = mapped_column(String(32), default="")
    paired_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    last_seen: Mapped[datetime] = mapped_column(DateTime, default=_now)
    unpaired_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    stale_notified_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)


class PairingCode(Base):
    __tablename__ = "pairing_code"

    id: Mapped[int] = mapped_column(primary_key=True)
    child_id: Mapped[int] = mapped_column(ForeignKey("child.id", ondelete="CASCADE"))
    code_hash: Mapped[str] = mapped_column(String(64), unique=True)
    expires_at: Mapped[datetime] = mapped_column(DateTime)
    used_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    created_by: Mapped[int | None] = mapped_column(
        ForeignKey("parent.id", ondelete="SET NULL"), nullable=True
    )


class Rule(Base):
    __tablename__ = "rule"

    id: Mapped[int] = mapped_column(primary_key=True)
    # NULL = gilt für alle Kinder. Eindeutigkeit stellt app/rules.py sicher.
    child_id: Mapped[int | None] = mapped_column(
        ForeignKey("child.id", ondelete="CASCADE"), nullable=True, index=True
    )
    target_type: Mapped[str] = mapped_column(String(10))
    target_id: Mapped[str] = mapped_column(String(64), index=True)
    title: Mapped[str] = mapped_column(String(300), default="")
    channel_name: Mapped[str] = mapped_column(String(200), default="")
    effect: Mapped[str] = mapped_column(String(10))
    note: Mapped[str] = mapped_column(Text, default="")
    created_by: Mapped[int | None] = mapped_column(
        ForeignKey("parent.id", ondelete="SET NULL"), nullable=True
    )
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class ContentRequest(Base):
    __tablename__ = "content_request"

    id: Mapped[int] = mapped_column(primary_key=True)
    child_id: Mapped[int] = mapped_column(ForeignKey("child.id", ondelete="CASCADE"), index=True)
    target_type: Mapped[str] = mapped_column(String(10))
    target_id: Mapped[str] = mapped_column(String(64))
    title: Mapped[str] = mapped_column(String(300), default="")
    channel_id: Mapped[str] = mapped_column(String(64), default="")
    channel_name: Mapped[str] = mapped_column(String(200), default="")
    url: Mapped[str] = mapped_column(String(300))
    thumbnail_url: Mapped[str] = mapped_column(String(500), default="")
    child_message: Mapped[str] = mapped_column(Text, default="")
    # Bei Kanal-Anfragen: das Video, von dem aus gefragt wurde ("nur dieses Video").
    source_video_id: Mapped[str] = mapped_column(String(64), default="")
    source_video_title: Mapped[str] = mapped_column(String(300), default="")
    status: Mapped[str] = mapped_column(String(10), default=STATUS_OPEN, index=True)
    decided_by: Mapped[int | None] = mapped_column(
        ForeignKey("parent.id", ondelete="SET NULL"), nullable=True
    )
    decided_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    reason: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now, index=True)


class WatchSession(Base):
    __tablename__ = "watch_session"

    id: Mapped[int] = mapped_column(primary_key=True)
    child_id: Mapped[int] = mapped_column(ForeignKey("child.id", ondelete="CASCADE"), index=True)
    # Von der App vergebene ID; macht wiederholte Zwischenstände idempotent.
    client_id: Mapped[str] = mapped_column(String(64), unique=True)
    video_id: Mapped[str] = mapped_column(String(64))
    channel_id: Mapped[str] = mapped_column(String(64), default="")
    title: Mapped[str] = mapped_column(String(300), default="")
    channel_name: Mapped[str] = mapped_column(String(200), default="")
    started_at: Mapped[datetime] = mapped_column(DateTime, index=True)
    played_seconds: Mapped[int] = mapped_column(Integer, default=0)
    in_reward_mode: Mapped[bool] = mapped_column(Boolean, default=False)
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class DeviceEvent(Base):
    __tablename__ = "device_event"

    id: Mapped[int] = mapped_column(primary_key=True)
    device_id: Mapped[int] = mapped_column(ForeignKey("device.id", ondelete="CASCADE"))
    kind: Mapped[str] = mapped_column(String(32))
    detail: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)


class MailOutbox(Base):
    __tablename__ = "mail_outbox"

    id: Mapped[int] = mapped_column(primary_key=True)
    to_address: Mapped[str] = mapped_column(String(255))
    subject: Mapped[str] = mapped_column(String(300))
    body: Mapped[str] = mapped_column(Text)
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    next_attempt_at: Mapped[datetime] = mapped_column(DateTime, default=_now, index=True)
    sent_at: Mapped[datetime | None] = mapped_column(DateTime, nullable=True)
    failed: Mapped[bool] = mapped_column(Boolean, default=False)
    last_error: Mapped[str] = mapped_column(Text, default="")
