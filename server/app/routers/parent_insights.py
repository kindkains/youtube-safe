"""Verlauf und Statistik pro Kind."""

from __future__ import annotations

from collections import defaultdict
from datetime import date, datetime, time, timedelta, timezone
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app import clock
from app.config import get_settings
from app.db import get_session
from app.deps import current_parent, get_child_or_404
from app.gate import decide
from app.models import Parent, WatchSession
from app.rules import as_views, rules_for_child
from app.schemas import ChannelStat, DayStat, HistoryEntryOut, StatsOut, UtcIn

router = APIRouter(prefix="/parent/children", tags=["parent"])


@router.get("/{child_id}/history", response_model=list[HistoryEntryOut])
def history(
    child_id: int,
    limit: int = 50,
    before: UtcIn | None = None,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> list[HistoryEntryOut]:
    child = get_child_or_404(session, child_id)
    query = (
        select(WatchSession)
        .where(WatchSession.child_id == child_id)
        .order_by(WatchSession.started_at.desc(), WatchSession.id.desc())
        .limit(max(1, min(limit, 200)))
    )
    if before is not None:
        query = query.where(WatchSession.started_at < before)
    views = as_views(rules_for_child(session, child_id))
    reward_active = child.reward_active(clock.now())
    result = []
    for item in session.scalars(query):
        decision = decide(
            views,
            child_id=child_id,
            video_id=item.video_id,
            channel_id=item.channel_id,
            reward_active=reward_active,
            reward_includes_blocked=child.reward_includes_blocked,
        )
        result.append(
            HistoryEntryOut(
                id=item.id,
                video_id=item.video_id,
                channel_id=item.channel_id,
                title=item.title,
                channel_name=item.channel_name,
                started_at=item.started_at,
                played_seconds=item.played_seconds,
                in_reward_mode=item.in_reward_mode,
                allowed_now=decision.allowed,
                reason_now=decision.reason,
            )
        )
    return result


def _local_day(value: datetime, zone: ZoneInfo) -> date:
    return value.replace(tzinfo=timezone.utc).astimezone(zone).date()


@router.get("/{child_id}/stats", response_model=StatsOut)
def stats(
    child_id: int,
    days: int = 7,
    _: Parent = Depends(current_parent),
    session: Session = Depends(get_session),
) -> StatsOut:
    get_child_or_404(session, child_id)
    days = max(1, min(days, 90))
    zone = ZoneInfo(get_settings().timezone)
    today = _local_day(clock.now(), zone)
    first_day = today - timedelta(days=days - 1)
    start_utc = (
        datetime.combine(first_day, time.min, tzinfo=zone).astimezone(timezone.utc).replace(tzinfo=None)
    )
    sessions = session.scalars(
        select(WatchSession).where(
            WatchSession.child_id == child_id, WatchSession.started_at >= start_utc
        )
    ).all()

    per_day: dict[date, list[int]] = {first_day + timedelta(days=i): [0, 0] for i in range(days)}
    per_channel: dict[str, list] = defaultdict(lambda: ["", 0])
    for item in sessions:
        day = _local_day(item.started_at, zone)
        if day not in per_day:
            continue
        per_day[day][0] += item.played_seconds
        if item.in_reward_mode:
            per_day[day][1] += item.played_seconds
        channel = per_channel[item.channel_id]
        channel[0] = channel[0] or item.channel_name
        channel[1] += item.played_seconds

    day_stats = [
        DayStat(date=d.isoformat(), seconds=v[0], reward_seconds=v[1]) for d, v in sorted(per_day.items())
    ]
    top = sorted(per_channel.items(), key=lambda kv: kv[1][1], reverse=True)[:10]
    return StatsOut(
        days=day_stats,
        top_channels=[ChannelStat(channel_id=cid, channel_name=v[0], seconds=v[1]) for cid, v in top],
        total_seconds=sum(d.seconds for d in day_stats),
        reward_seconds=sum(d.reward_seconds for d in day_stats),
    )
