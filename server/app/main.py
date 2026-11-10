"""FastAPI-Anwendung."""

from __future__ import annotations

from fastapi import FastAPI

from app.routers import kid, parent_admin, parent_auth, parent_children, parent_requests, parent_rules

API_PREFIX = "/api/v1"

app = FastAPI(title="youtube-safe")
for router in (
    parent_auth.router,
    parent_admin.router,
    parent_children.router,
    parent_rules.router,
    parent_requests.router,
    kid.router,
):
    app.include_router(router, prefix=API_PREFIX)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
