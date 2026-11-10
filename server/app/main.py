"""FastAPI-Anwendung."""

from __future__ import annotations

from fastapi import FastAPI

from app.routers import parent_admin, parent_auth

API_PREFIX = "/api/v1"

app = FastAPI(title="youtube-safe")
app.include_router(parent_auth.router, prefix=API_PREFIX)
app.include_router(parent_admin.router, prefix=API_PREFIX)


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}
