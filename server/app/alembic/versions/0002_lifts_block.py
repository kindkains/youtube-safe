"""Anfrage merkt sich, ob sie eine Sperre aufheben soll

Revision ID: 0002
Revises: 0001
Create Date: 2026-11-16 10:00:00
"""
from __future__ import annotations

from alembic import op
import sqlalchemy as sa


revision = '0002'
down_revision = '0001'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        'content_request',
        sa.Column('lifts_block', sa.Boolean(), nullable=False, server_default=sa.false()),
    )


def downgrade() -> None:
    op.drop_column('content_request', 'lifts_block')
