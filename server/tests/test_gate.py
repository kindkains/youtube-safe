"""Die Entscheidungslogik gegen die gemeinsame Testtabelle."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.gate import Decision, RuleView, decide

CASES_FILE = Path(__file__).resolve().parents[2] / "shared" / "gate-cases.json"
CASES = json.loads(CASES_FILE.read_text(encoding="utf-8"))["cases"]


@pytest.mark.parametrize("case", CASES, ids=[c["name"] for c in CASES])
def test_gate_case(case):
    rules = [RuleView(**r) for r in case["rules"]]
    result = decide(
        rules,
        child_id=1,
        video_id=case["video_id"],
        channel_id=case["channel_id"],
        reward_active=case["reward_active"],
        reward_includes_blocked=case["reward_includes_blocked"],
    )
    assert result == Decision(**case["expected"])


def test_cases_file_is_not_empty():
    assert len(CASES) >= 10
