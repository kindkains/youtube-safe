"""FastAPI-Anwendung."""

from __future__ import annotations

import asyncio
import logging
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from app import jobs
from app.config import get_settings
from app.routers import (
    kid,
    parent_admin,
    parent_auth,
    parent_children,
    parent_insights,
    parent_requests,
    parent_reward,
    parent_rules,
)

API_PREFIX = "/api/v1"

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(name)s %(message)s")


@asynccontextmanager
async def lifespan(_: FastAPI):
    task = asyncio.create_task(jobs.periodic_loop()) if get_settings().jobs_enabled else None
    yield
    if task is not None:
        task.cancel()


app = FastAPI(title="youtube-safe", lifespan=lifespan)

for router in (
    parent_auth.router,
    parent_admin.router,
    parent_children.router,
    parent_rules.router,
    parent_requests.router,
    parent_reward.router,
    parent_insights.router,
    kid.router,
):
    app.include_router(router, prefix=API_PREFIX)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


# Signierte APKs und version.json legt die CI-Pipeline hier ab (Plan 4).
_updates = Path(get_settings().updates_dir)
if _updates.is_dir():
    app.mount("/updates", StaticFiles(directory=_updates), name="updates")
