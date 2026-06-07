"""Claude Code headless adapter.

Invocation: `claude -p <prompt> --output-format stream-json` with cwd = workspace.
The stream is consumed line-by-line: persisted verbatim to transcript.jsonl,
token usage accumulated for the live budget watchdog, and the final `result`
event provides authoritative usage/cost/turn numbers (cost_source=reported).
"""

from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path

from ..config import BudgetCfg
from .base import AgentAdapter, AgentResult


class ClaudeCodeAdapter(AgentAdapter):
    name = "claude_code"

    def cli_version(self) -> str:
        try:
            return subprocess.run(["claude", "--version"], capture_output=True,
                                  text=True, timeout=30).stdout.strip()
        except Exception:
            return "unknown"

    def run(self, workspace: Path, prompt: str, budget: BudgetCfg,
            model_id: str, transcript_path: Path, stdout_path: Path,
            env: dict[str, str]) -> AgentResult:
        cmd = [
            "claude", "-p", prompt,
            "--output-format", "stream-json",
            "--verbose",
            "--max-turns", str(budget.max_turns),
            "--model", model_id,
            "--dangerously-skip-permissions",
        ]
        deadline = time.monotonic() + budget.max_wall_minutes * 60
        start = time.monotonic()
        killed_reason: str | None = None
        cum_out_tokens = 0
        result_event: dict | None = None
        tool_invocations = 0

        proc_env = {**os.environ, **env,
                    # never inherit interactive session state
                    "CLAUDE_CODE_ENTRYPOINT": "harness"}

        with open(transcript_path, "w") as transcript, open(stdout_path, "w") as out:
            proc = subprocess.Popen(cmd, cwd=workspace, stdout=subprocess.PIPE,
                                    stderr=out, text=True, env=proc_env)
            assert proc.stdout is not None
            try:
                for line in proc.stdout:
                    transcript.write(line)
                    transcript.flush()
                    try:
                        ev = json.loads(line)
                    except json.JSONDecodeError:
                        continue
                    if ev.get("type") == "assistant":
                        usage = (ev.get("message") or {}).get("usage") or {}
                        cum_out_tokens += usage.get("output_tokens", 0) or 0
                        for blk in (ev.get("message") or {}).get("content") or []:
                            if isinstance(blk, dict) and blk.get("type") == "tool_use":
                                tool_invocations += 1
                    elif ev.get("type") == "result":
                        result_event = ev
                    # budget watchdog
                    if time.monotonic() > deadline:
                        killed_reason = "wall"
                        proc.kill()
                        break
                    if cum_out_tokens > budget.max_tokens:
                        killed_reason = "tokens"
                        proc.kill()
                        break
                remaining = max(10.0, deadline - time.monotonic())
                exit_code = proc.wait(timeout=remaining)
            except subprocess.TimeoutExpired:
                killed_reason = killed_reason or "wall"
                proc.kill()
                exit_code = proc.wait()

        wall = time.monotonic() - start
        res = AgentResult(exit_code=exit_code, wall_seconds=wall,
                          killed_reason=killed_reason,
                          tool_invocations=tool_invocations,
                          cli_version=self.cli_version())
        if result_event:
            usage = result_event.get("usage") or {}
            res.tokens_in = usage.get("input_tokens")
            res.tokens_out = usage.get("output_tokens")
            res.cache_tokens = (usage.get("cache_read_input_tokens") or 0) + \
                               (usage.get("cache_creation_input_tokens") or 0)
            res.turns = result_event.get("num_turns")
            res.cost_usd = result_event.get("total_cost_usd")
            res.cost_source = "reported" if res.cost_usd is not None else None
            if result_event.get("duration_api_ms") is not None:
                res.active_agent_seconds = result_event["duration_api_ms"] / 1000.0
            res.extra["result_subtype"] = result_event.get("subtype")
        return res


class NullAdapter(AgentAdapter):
    """$0 pipeline-validation agent: writes nothing, exits immediately.
    Grades the bare skeleton — validates provisioning, build/boot, suites,
    perf, metrics, and persistence without any model spend."""
    name = "null"

    def run(self, workspace: Path, prompt: str, budget: BudgetCfg,
            model_id: str, transcript_path: Path, stdout_path: Path,
            env: dict[str, str]) -> AgentResult:
        transcript_path.write_text("")
        stdout_path.write_text("null agent: no-op\n")
        return AgentResult(exit_code=0, wall_seconds=0.0, tokens_in=0, tokens_out=0,
                           turns=0, tool_invocations=0, cost_usd=0.0,
                           cost_source="reported", cli_version="null")


ADAPTERS: dict[str, AgentAdapter] = {"claude_code": ClaudeCodeAdapter(),
                                     "null": NullAdapter()}


def get_adapter(name: str) -> AgentAdapter:
    if name not in ADAPTERS:
        raise KeyError(f"agent adapter '{name}' not implemented yet "
                       f"(available: {sorted(ADAPTERS)})")
    return ADAPTERS[name]
