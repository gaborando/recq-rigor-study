#!/usr/bin/env python3
"""Pace the Opus opus-all sweep across subscription session-limit windows.

Opus burns the ~5h subscription session quota after roughly one cell, so the
whole 9-cell matrix can't run back-to-back. This pacer runs the remaining cells
one at a time:

  1. cheap quota probe (`claude -p`) — if the session limit is already hit,
     parse the reset time from the error and sleep until then (+5m buffer);
  2. otherwise run the single cell via the harness;
  3. if the cell's run still ended on a session-limit error (limit hit
     mid-run), discard that dir and wait for the next window;
  4. a cell counts as DONE once it has a run dir whose final result is NOT a
     session-limit error. Already-valid cells are skipped, so the pacer is
     restartable.

Subscription billing only — no API key. Runs detached; survives across resets.
Progress → runs/_pace_opus.log.
"""
from __future__ import annotations

import datetime
import glob
import json
import os
import re
import shutil
import subprocess
import time
from zoneinfo import ZoneInfo

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROME = ZoneInfo("Europe/Rome")
MODEL = "claude-opus"          # registry key -> claude-opus-4-8
TASK = "t1"
MAX_WINDOWS_PER_CELL = 8       # safety cap against an infinite wait loop

# Remaining cells (order-inventory/arm_a_evento already landed cleanly).
CELLS = [
    ("order-inventory", "arm_b_spring"),
    ("order-inventory", "arm_c_axon"),
    ("todolist", "arm_a_evento"),
    ("todolist", "arm_b_spring"),
    ("todolist", "arm_c_axon"),
    ("flight", "arm_a_evento"),
    ("flight", "arm_b_spring"),
    ("flight", "arm_c_axon"),
]

LIMIT_RE = re.compile(r"session limit", re.I)
RESET_RE = re.compile(r"resets\s+(\d{1,2}):(\d{2})\s*([ap]m)?", re.I)
PROC_ENV = {**os.environ, "CLAUDE_CODE_ENTRYPOINT": "harness"}


def log(msg: str) -> None:
    ts = datetime.datetime.now(ROME).strftime("%Y-%m-%d %H:%M:%S %Z")
    print(f"[{ts}] {msg}", flush=True)


def _newest_dir(domain: str, arm: str) -> str | None:
    ds = sorted(glob.glob(os.path.join(
        REPO, "runs", f"{domain}__{arm}__{MODEL}__{TASK}__rep1__*")))
    return ds[-1] if ds else None


def _final_result_event(d: str) -> dict | None:
    t = os.path.join(d, "agent", "transcript.jsonl")
    if not os.path.exists(t):
        return None
    last = None
    with open(t) as f:
        for line in f:
            try:
                ev = json.loads(line)
            except json.JSONDecodeError:
                continue
            if ev.get("type") == "result":
                last = ev
    return last


def _is_limit_event(ev: dict | None) -> bool:
    return bool(ev and ev.get("is_error")
                and LIMIT_RE.search(str(ev.get("result") or "")))


def cell_is_done(domain: str, arm: str) -> bool:
    d = _newest_dir(domain, arm)
    if not d or not os.path.exists(os.path.join(d, "run.json")):
        return False
    return not _is_limit_event(_final_result_event(d))


def probe_quota() -> tuple[bool, str]:
    """Cheap quota check. Returns (ok, reset_text)."""
    try:
        r = subprocess.run(
            ["claude", "-p", "Reply with exactly: OK",
             "--model", "claude-opus-4-8", "--output-format", "json"],
            cwd=REPO, capture_output=True, text=True, timeout=180, env=PROC_ENV)
    except subprocess.TimeoutExpired:
        return True, ""        # inconclusive — let the real run surface it
    txt = r.stdout
    try:
        j = json.loads(r.stdout)
        txt = str(j.get("result") or "")
    except Exception:
        pass
    if LIMIT_RE.search(txt):
        return False, txt
    return True, ""


def seconds_until_reset(text: str) -> float:
    now = datetime.datetime.now(ROME)
    m = RESET_RE.search(text or "")
    if not m:
        return 5 * 3600 + 300          # fallback: ~one 5h window
    hh, mm = int(m.group(1)), int(m.group(2))
    ap = (m.group(3) or "").lower()
    if ap == "pm" and hh != 12:
        hh += 12
    if ap == "am" and hh == 12:
        hh = 0
    target = now.replace(hour=hh, minute=mm, second=0, microsecond=0)
    if target <= now:
        target += datetime.timedelta(days=1)
    return (target - now).total_seconds() + 300   # +5m buffer


def wait_for_reset(text: str) -> None:
    secs = seconds_until_reset(text)
    eta = (datetime.datetime.now(ROME) + datetime.timedelta(seconds=secs))
    log(f"  waiting {int(secs)}s for session reset (~{eta:%H:%M %Z})")
    end = time.monotonic() + secs
    while time.monotonic() < end:
        time.sleep(min(600, max(1, end - time.monotonic())))


def run_cell(domain: str, arm: str) -> tuple[str, str]:
    log(f"RUN {domain}/{arm}")
    subprocess.run(
        ["uv", "run", "python", "-m", "harness", "run",
         "--domain", domain, "--arm", arm, "--model", MODEL,
         "--task", TASK, "--yes"],
        cwd=REPO, env=PROC_ENV)
    d = _newest_dir(domain, arm)
    ev = _final_result_event(d) if d else None
    if _is_limit_event(ev):
        log(f"  hit session limit mid-run; discarding {os.path.basename(d)}")
        shutil.rmtree(d, ignore_errors=True)
        return "limit", str(ev.get("result") or "")
    rj = os.path.join(d, "run.json") if d else None
    if rj and os.path.exists(rj):
        r = json.load(open(rj))
        fn, b = r["functional"], r["budget_spent"]
        log(f"  DONE acc={fn['acceptance_passed']}/{fn['acceptance_total']} "
            f"build={fn['builds_cleanly']} boot={fn['boots_cleanly']} "
            f"turns={b['turns']} cost=${b['cost_usd']}")
    else:
        log(f"  finished but no run.json in {d} — accepting as-is")
    return "done", ""


def main() -> None:
    log(f"PACER START — {len(CELLS)} candidate cells (Opus, subscription)")
    for domain, arm in CELLS:
        if cell_is_done(domain, arm):
            log(f"SKIP {domain}/{arm} (already has a valid run)")
            continue
        for window in range(1, MAX_WINDOWS_PER_CELL + 1):
            ok, reset_text = probe_quota()
            if not ok:
                log(f"{domain}/{arm}: quota exhausted (window {window})")
                wait_for_reset(reset_text)
                continue
            status, msg = run_cell(domain, arm)
            if status == "done":
                break
            wait_for_reset(msg)        # limit hit during the run
        else:
            log(f"GAVE UP on {domain}/{arm} after {MAX_WINDOWS_PER_CELL} windows")
    log("PACER COMPLETE")
    subprocess.run(["python3", "runs/status.py"], cwd=REPO)


if __name__ == "__main__":
    main()
