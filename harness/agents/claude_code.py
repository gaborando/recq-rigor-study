"""Claude Code headless adapter.

Invocation: `claude -p <prompt> --output-format stream-json` with cwd = workspace.
The stream is consumed line-by-line: persisted verbatim to transcript.jsonl,
token usage accumulated for the live budget watchdog, and the final `result`
event provides authoritative usage/cost/turn numbers (cost_source=reported).

Transient API errors (socket closed, request timed out, connection refused, …)
that abort the agent mid-run are NOT a cell outcome — they are infrastructure
flakiness. The adapter detects them from the `result` event and RESUMES the
session (`--resume`/`--continue`, which sees the auto-committed partial work),
accumulating tokens/turns/cost/wall across attempts, bounded by the wall+token
budget and a max-attempts cap. Real outcomes (clean finish, max-turns, genuine
agent errors) are never retried.
"""

from __future__ import annotations

import json
import os
import re
import subprocess
import time
from pathlib import Path

from ..config import BudgetCfg
from .base import AgentAdapter, AgentResult

# Phrases (in the `result` event text, only when is_error) that mark a transient
# transport/API failure worth resuming rather than recording as the cell's result.
_TRANSIENT_API_RE = re.compile(
    r"socket connection.*clos|connection.*clos|closed unexpectedly|"
    r"request timed out|timed out|connection refused|connection reset|"
    r"unable to connect|could not connect|connection error|network error|"
    r"overloaded|service unavailable|internal server error|bad gateway|"
    r"\b50[02-4]\b|\b429\b|api error",
    re.IGNORECASE,
)


def _is_transient_api_error(result_event: dict | None) -> bool:
    """True only for an errored result whose text looks like transport/API
    flakiness — never for a clean finish or a max-turns/budget stop."""
    if not result_event or not result_event.get("is_error"):
        return False
    if result_event.get("subtype") == "error_max_turns":
        return False
    return bool(_TRANSIENT_API_RE.search(str(result_event.get("result") or "")))


class ClaudeCodeAdapter(AgentAdapter):
    name = "claude_code"

    def cli_version(self) -> str:
        try:
            return subprocess.run(["claude", "--version"], capture_output=True,
                                  text=True, timeout=30).stdout.strip()
        except Exception:
            return "unknown"

    # max agent invocations per cell (1 initial + up to 3 resumes after a
    # transient API error); also bounded by the wall + token budget.
    MAX_ATTEMPTS = 4
    RESUME_PROMPT = ("Continue from where you left off. Keep iterating — build, run "
                     "the acceptance suite in ./acceptance-tests, fix failures — until "
                     "it is green or you are genuinely blocked.")

    def _stream_once(self, cmd, cwd, env, transcript, out, deadline, max_tokens,
                     cum_out_tokens):
        """One `claude` invocation; stream → transcript, enforce the SHARED wall
        deadline + CUMULATIVE token watchdog. Returns the per-attempt outcome."""
        killed_reason = None
        result_event = None
        tool_invocations = 0
        session_id = None
        proc = subprocess.Popen(cmd, cwd=cwd, stdout=subprocess.PIPE,
                                stderr=out, text=True, env=env)
        assert proc.stdout is not None
        try:
            for line in proc.stdout:
                transcript.write(line)
                transcript.flush()
                try:
                    ev = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if ev.get("session_id"):
                    session_id = ev["session_id"]
                if ev.get("type") == "assistant":
                    usage = (ev.get("message") or {}).get("usage") or {}
                    cum_out_tokens += usage.get("output_tokens", 0) or 0
                    for blk in (ev.get("message") or {}).get("content") or []:
                        if isinstance(blk, dict) and blk.get("type") == "tool_use":
                            tool_invocations += 1
                elif ev.get("type") == "result":
                    result_event = ev
                if time.monotonic() > deadline:
                    killed_reason = "wall"
                    proc.kill()
                    break
                if cum_out_tokens > max_tokens:
                    killed_reason = "tokens"
                    proc.kill()
                    break
            remaining = max(10.0, deadline - time.monotonic())
            exit_code = proc.wait(timeout=remaining)
        except subprocess.TimeoutExpired:
            killed_reason = killed_reason or "wall"
            proc.kill()
            exit_code = proc.wait()
        return {"exit_code": exit_code, "result_event": result_event,
                "tool_invocations": tool_invocations, "cum_out_tokens": cum_out_tokens,
                "killed_reason": killed_reason, "session_id": session_id}

    def run(self, workspace: Path, prompt: str, budget: BudgetCfg,
            model_id: str, transcript_path: Path, stdout_path: Path,
            env: dict[str, str]) -> AgentResult:
        base = ["claude", "--output-format", "stream-json", "--verbose",
                "--model", model_id, "--dangerously-skip-permissions"]
        proc_env = {**os.environ, **env,
                    "CLAUDE_CODE_ENTRYPOINT": "harness"}  # never inherit session state
        deadline = time.monotonic() + budget.max_wall_minutes * 60
        start = time.monotonic()

        tot = {"in": 0, "out": 0, "cache": 0, "turns": 0, "tools": 0, "cost": 0.0, "api_s": 0.0}
        cum_out_tokens = 0
        killed_reason: str | None = None
        session_id: str | None = None
        last_result: dict | None = None
        transient_retries = 0
        attempts = 0
        exit_code = 0

        with open(transcript_path, "w") as transcript, open(stdout_path, "w") as out:
            while attempts < self.MAX_ATTEMPTS:
                attempts += 1
                remaining_turns = budget.max_turns - tot["turns"]
                if remaining_turns < 1:
                    break
                if attempts == 1:
                    cmd = base + ["--max-turns", str(remaining_turns), "-p", prompt]
                else:
                    resume = ["--resume", session_id] if session_id else ["--continue"]
                    cmd = base + resume + ["--max-turns", str(remaining_turns),
                                           "-p", self.RESUME_PROMPT]
                    transcript.write(json.dumps({
                        "type": "harness", "event": "resume_after_transient_api_error",
                        "attempt": attempts, "session_id": session_id}) + "\n")
                    transcript.flush()

                r = self._stream_once(cmd, workspace, proc_env, transcript, out,
                                      deadline, budget.max_tokens, cum_out_tokens)
                cum_out_tokens = r["cum_out_tokens"]
                exit_code = r["exit_code"]
                tot["tools"] += r["tool_invocations"]
                if r["session_id"]:
                    session_id = r["session_id"]
                ev = r["result_event"]
                if ev:
                    last_result = ev
                    u = ev.get("usage") or {}
                    tot["in"] += u.get("input_tokens") or 0
                    tot["out"] += u.get("output_tokens") or 0
                    tot["cache"] += (u.get("cache_read_input_tokens") or 0) + \
                                    (u.get("cache_creation_input_tokens") or 0)
                    tot["turns"] += ev.get("num_turns") or 0
                    tot["cost"] += ev.get("total_cost_usd") or 0.0
                    if ev.get("duration_api_ms") is not None:
                        tot["api_s"] += ev["duration_api_ms"] / 1000.0

                if r["killed_reason"]:                 # wall/tokens exhausted — a real stop
                    killed_reason = r["killed_reason"]
                    break
                if _is_transient_api_error(ev) and time.monotonic() < deadline:
                    transient_retries += 1
                    continue                            # resume the session
                break                                   # clean finish / max-turns / genuine error

        wall = time.monotonic() - start
        res = AgentResult(exit_code=exit_code, wall_seconds=wall,
                          killed_reason=killed_reason, tool_invocations=tot["tools"],
                          cli_version=self.cli_version())
        if last_result is not None:
            res.tokens_in = tot["in"]
            res.tokens_out = tot["out"]
            res.cache_tokens = tot["cache"]
            res.turns = tot["turns"]
            res.cost_usd = tot["cost"]
            res.cost_source = "reported"
            res.active_agent_seconds = tot["api_s"] or None
            res.extra["result_subtype"] = last_result.get("subtype")
        res.extra["attempts"] = attempts
        res.extra["transient_retries"] = transient_retries
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
