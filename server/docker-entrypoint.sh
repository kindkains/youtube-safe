#!/usr/bin/env bash
#
# Migrationen einspielen, ersten Elternteil anlegen, dann die Anwendung. Beides idempotent.

set -Eeuo pipefail

echo "[youtube-safe] Migrationen ..."
alembic upgrade head

echo "[youtube-safe] Erster Elternteil ..."
python -m app.bootstrap

echo "[youtube-safe] Start."
exec "$@"
