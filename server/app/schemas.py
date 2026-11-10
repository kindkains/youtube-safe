"""Ein- und Ausgabeformate der API.

Zeitpunkte gehen als ISO-8601 mit „Z“ raus; eingehende Zeitpunkte mit Zeitzone werden
nach naivem UTC umgerechnet.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Annotated, Literal

from pydantic import AfterValidator, BaseModel, ConfigDict, Field, PlainSerializer, model_validator

from app import clock


def _serialize_utc(value: datetime) -> str:
    return value.replace(tzinfo=timezone.utc).isoformat().replace("+00:00", "Z")


UtcOut = Annotated[datetime, PlainSerializer(_serialize_utc, return_type=str)]
UtcIn = Annotated[datetime, AfterValidator(clock.to_naive_utc)]

TargetType = Literal["channel", "video"]
Effect = Literal["allow", "block"]
DisplayMode = Literal["compact", "full"]
Status = Literal["open", "approved", "rejected"]


class OrmModel(BaseModel):
    model_config = ConfigDict(from_attributes=True)


# --- Eltern ----------------------------------------------------------------------


class LoginIn(BaseModel):
    email: str
    password: str


class ParentOut(OrmModel):
    id: int
    name: str
    email: str
    mail_new_request: bool
    mail_alerts: bool


class LoginOut(BaseModel):
    token: str
    parent: ParentOut


class ParentCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)
    email: str = Field(min_length=3, max_length=255)
    password: str = Field(min_length=8)


class MeUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=100)
    mail_new_request: bool | None = None
    mail_alerts: bool | None = None
    password: str | None = Field(default=None, min_length=8)


class PushInfoOut(BaseModel):
    ntfy_url: str
    ntfy_topic: str


# --- Kinder und Geräte -----------------------------------------------------------


class ChildCreate(BaseModel):
    name: str = Field(min_length=1, max_length=100)


class ChildUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=100)
    display_mode: DisplayMode | None = None
    shorts_enabled: bool | None = None


class ChildOut(BaseModel):
    id: int
    name: str
    display_mode: DisplayMode
    shorts_enabled: bool
    reward_active: bool
    reward_until: UtcOut | None
    reward_includes_blocked: bool


class DeviceOut(OrmModel):
    id: int
    child_id: int
    app_version: str
    paired_at: UtcOut
    last_seen: UtcOut
    unpaired_at: UtcOut | None


class PairingCodeOut(BaseModel):
    code: str
    expires_at: UtcOut
    qr_payload: str


class PairIn(BaseModel):
    code: str = Field(min_length=8, max_length=8)
    app_version: str = Field(default="", max_length=32)


class PairOut(BaseModel):
    token: str
    child_id: int
    child_name: str
    ntfy_url: str
    ntfy_topic: str


# --- Regeln ----------------------------------------------------------------------


class RuleIn(BaseModel):
    child_id: int | None
    target_type: TargetType
    target_id: str = Field(min_length=1, max_length=64)
    effect: Effect
    title: str = Field(default="", max_length=300)
    channel_name: str = Field(default="", max_length=200)
    note: str = Field(default="", max_length=2000)


class RuleUpdate(BaseModel):
    effect: Effect | None = None
    note: str | None = Field(default=None, max_length=2000)


class RuleOut(OrmModel):
    id: int
    child_id: int | None
    target_type: TargetType
    target_id: str
    title: str
    channel_name: str
    effect: Effect
    note: str
    created_at: UtcOut
    updated_at: UtcOut


class KidRuleOut(OrmModel):
    child_id: int | None
    target_type: TargetType
    target_id: str
    effect: Effect


class KidStateOut(BaseModel):
    server_time: UtcOut
    child: ChildOut
    rules: list[KidRuleOut]


# --- Anfragen --------------------------------------------------------------------


class RequestIn(BaseModel):
    target_type: TargetType
    target_id: str = Field(min_length=1, max_length=64)
    title: str = Field(default="", max_length=300)
    channel_id: str = Field(default="", max_length=64)
    channel_name: str = Field(default="", max_length=200)
    thumbnail_url: str = Field(default="", max_length=500)
    message: str = Field(default="", max_length=500)
    source_video_id: str = Field(default="", max_length=64)
    source_video_title: str = Field(default="", max_length=300)


class RequestOut(BaseModel):
    id: int
    child_id: int
    child_name: str
    target_type: TargetType
    target_id: str
    title: str
    channel_id: str
    channel_name: str
    url: str
    thumbnail_url: str
    child_message: str
    source_video_id: str
    source_video_title: str
    status: Status
    decided_by_name: str | None
    decided_at: UtcOut | None
    reason: str
    created_at: UtcOut


class DecisionIn(BaseModel):
    action: Literal["approve", "approve_video_only", "reject", "reject_and_block"]
    scope: Literal["child", "all"] = "child"
    reason: str = Field(default="", max_length=500)


# --- Belohnungsmodus -------------------------------------------------------------

MAX_REWARD_MINUTES = 60 * 24 * 30


class RewardIn(BaseModel):
    minutes: int | None = Field(default=None, ge=1, le=MAX_REWARD_MINUTES)
    until: UtcIn | None = None
    include_blocked: bool = False

    @model_validator(mode="after")
    def _exactly_one(self) -> "RewardIn":
        if (self.minutes is None) == (self.until is None):
            raise ValueError("Genau eines von 'minutes' oder 'until' angeben.")
        return self


# --- Schauzeit, Verlauf, Statistik -----------------------------------------------


class WatchEntryIn(BaseModel):
    client_id: str = Field(min_length=8, max_length=64)
    video_id: str = Field(min_length=1, max_length=64)
    channel_id: str = Field(default="", max_length=64)
    title: str = Field(default="", max_length=300)
    channel_name: str = Field(default="", max_length=200)
    started_at: UtcIn
    played_seconds: int = Field(ge=0, le=86400)


class WatchBatchIn(BaseModel):
    entries: list[WatchEntryIn] = Field(max_length=200)


class EventIn(BaseModel):
    kind: Literal["extractor_error"]
    detail: str = Field(default="", max_length=2000)


class HistoryEntryOut(BaseModel):
    id: int
    video_id: str
    channel_id: str
    title: str
    channel_name: str
    started_at: UtcOut
    played_seconds: int
    in_reward_mode: bool
    allowed_now: bool
    reason_now: str


class DayStat(BaseModel):
    date: str
    seconds: int
    reward_seconds: int


class ChannelStat(BaseModel):
    channel_id: str
    channel_name: str
    seconds: int


class StatsOut(BaseModel):
    days: list[DayStat]
    top_channels: list[ChannelStat]
    total_seconds: int
    reward_seconds: int
