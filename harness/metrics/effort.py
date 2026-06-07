"""TIME + BUDGET KPIs: agent-CLI usage + workspace event log + transcript heuristics."""

from __future__ import annotations

import json
import re
from pathlib import Path

from ..agents.base import AgentResult
from ..workspace import Workspace

TEST_CMD = re.compile(r"\bmake\s+test\b|\bpytest\b")
BUILD_CMD = re.compile(r"\bmake\s+(build|run|test)\b|\bmvn\b.*\b(package|verify|compile|install)\b")
BUILD_FAILURE = re.compile(r"BUILD FAILURE|COMPILATION ERROR|cannot find symbol|compilation failed",
                           re.IGNORECASE)


def _bash_events(w: Workspace) -> list[dict]:
    f = w.dir / ".harness-events.jsonl"
    if not f.exists():
        return []
    out = []
    for line in f.read_text().splitlines():
        try:
            out.append(json.loads(line))
        except json.JSONDecodeError:
            continue
    return out


def _transcript_bash_results(transcript: Path) -> list[dict]:
    """Chronological [{cmd, output}] for Bash tool calls, correlated by tool_use id."""
    if not transcript.exists():
        return []
    pending: dict[str, str] = {}
    order: list[str] = []
    outputs: dict[str, str] = {}
    for line in transcript.read_text().splitlines():
        try:
            ev = json.loads(line)
        except json.JSONDecodeError:
            continue
        msg = ev.get("message") or {}
        for blk in (msg.get("content") or []) if isinstance(msg.get("content"), list) else []:
            if not isinstance(blk, dict):
                continue
            if ev.get("type") == "assistant" and blk.get("type") == "tool_use" \
                    and blk.get("name") == "Bash":
                tid = blk.get("id", "")
                pending[tid] = (blk.get("input") or {}).get("command", "")
                order.append(tid)
            elif ev.get("type") == "user" and blk.get("type") == "tool_result":
                tid = blk.get("tool_use_id", "")
                content = blk.get("content")
                if isinstance(content, list):
                    text = " ".join(c.get("text", "") for c in content if isinstance(c, dict))
                else:
                    text = str(content)
                outputs[tid] = text
    return [{"cmd": pending.get(t, ""), "output": outputs.get(t, "")} for t in order]


def compute(w: Workspace, agent: AgentResult, started_ts: float) -> dict:
    events = _bash_events(w)
    bash_results = _transcript_bash_results(w.root / "agent" / "transcript.jsonl")

    compile_failures = sum(
        1 for r in bash_results
        if BUILD_CMD.search(r["cmd"]) and BUILD_FAILURE.search(r["output"] or "")
    )

    # correlate the i-th Bash transcript entry with the i-th hook event (both chronological)
    n = min(len(events), len(bash_results))
    test_runs = [(events[i]["ts"], bash_results[i]["output"] or "")
                 for i in range(n) if TEST_CMD.search(bash_results[i]["cmd"])]

    first_test_ts = test_runs[0][0] if test_runs else None
    seconds_to_all_green = None
    for ts, output in test_runs:
        if re.search(r"\bfailed\b|\berror\b", output, re.IGNORECASE):
            continue
        if re.search(r"\d+ passed", output):
            seconds_to_all_green = max(0.0, ts - started_ts)
            break

    seconds_to_first_green_build = None
    for i in range(n):
        if BUILD_CMD.search(bash_results[i]["cmd"]) and \
                not BUILD_FAILURE.search(bash_results[i]["output"] or ""):
            seconds_to_first_green_build = max(0.0, events[i]["ts"] - started_ts)
            break

    out = {
        "compile_failures": compile_failures,
        "test_runs": len(test_runs),
        "first_test_ts": first_test_ts,
        "seconds_to_first_green_build": seconds_to_first_green_build,
        "seconds_to_all_green": seconds_to_all_green,
        "tokens_in": agent.tokens_in,
        "tokens_out": agent.tokens_out,
        "cache_tokens": agent.cache_tokens,
        "turns": agent.turns,
        "tool_invocations": agent.tool_invocations,
        "cost_usd": agent.cost_usd,
        "cost_source": agent.cost_source,
        "active_agent_seconds": agent.active_agent_seconds,
        "agent_wall_seconds": agent.wall_seconds,
        "killed_reason": agent.killed_reason,
    }
    (w.root / "metrics" / "effort.json").write_text(json.dumps(out, indent=2))
    return out
