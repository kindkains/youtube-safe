"""Die Migration muss genau das Schema erzeugen, das die Modelle beschreiben."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

from sqlalchemy import create_engine, inspect

from app.db import Base

SERVER_DIR = Path(__file__).resolve().parents[1]


def test_upgrade_head_creates_all_tables(tmp_path):
    db_file = tmp_path / "migration.db"
    env = {"DATABASE_URL": f"sqlite:///{db_file}", "PATH": "/usr/bin:/bin"}
    subprocess.run(
        [sys.executable, "-m", "alembic", "upgrade", "head"],
        cwd=SERVER_DIR, env=env, check=True, capture_output=True,
    )
    tables = set(inspect(create_engine(f"sqlite:///{db_file}")).get_table_names())
    assert set(Base.metadata.tables) <= tables
