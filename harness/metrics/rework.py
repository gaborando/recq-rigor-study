"""QUALITY KPI: rework churn.

'Original design' = the workspace tree at the FIRST full acceptance-suite
execution (`make test`). Every commit after that timestamp is rework triggered
by test feedback. The per-iteration commits are produced by the PostToolUse
auto-commit hook during the agent run.
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path

from ..workspace import Workspace


def _git(w: Workspace, *args: str) -> str:
    return subprocess.run(["git", *args], cwd=w.dir, capture_output=True,
                          text=True, check=False).stdout


def compute(w: Workspace, first_test_ts: float | None,
            tests_failing_at_end: int | None) -> dict:
    log = _git(w, "log", "--format=%H %ct", "--reverse").strip().splitlines()
    commits = [(h, int(ts)) for h, ts in (line.split() for line in log if line)]
    iterations_total = max(0, len(commits) - 1)  # minus the baseline commit

    out: dict = {
        "iterations_total": iterations_total,
        "iterations_after_first_test_run": None,
        "loc_churned_after_first_test_run": None,
        "files_touched_in_rework": None,
        "rework_ratio": None,
        "tests_failing_at_end": tests_failing_at_end,
    }

    if first_test_ts is not None and commits:
        rework = [h for h, ts in commits[1:] if ts > first_test_ts]
        out["iterations_after_first_test_run"] = len(rework)
        if rework:
            # diff from the last pre-test commit to HEAD, source files only
            pre = [h for h, ts in commits if ts <= first_test_ts]
            base = pre[-1] if pre else commits[0][0]
            numstat = _git(w, "diff", "--numstat", base, "HEAD", "--", "src/")
            churn, files = 0, 0
            for line in numstat.strip().splitlines():
                parts = line.split("\t")
                if len(parts) == 3 and parts[0].isdigit() and parts[1].isdigit():
                    churn += int(parts[0]) + int(parts[1])
                    files += 1
            out["loc_churned_after_first_test_run"] = churn
            out["files_touched_in_rework"] = files
        else:
            out["loc_churned_after_first_test_run"] = 0
            out["files_touched_in_rework"] = 0

        final_loc = 0
        ls = _git(w, "ls-files", "src/").strip().splitlines()
        for f in ls:
            p = w.dir / f
            if p.suffix == ".java" and p.exists():
                final_loc += sum(1 for ln in p.read_text(errors="ignore").splitlines()
                                 if ln.strip())
        if final_loc and out["loc_churned_after_first_test_run"] is not None:
            out["rework_ratio"] = round(out["loc_churned_after_first_test_run"] / final_loc, 4)

    (w.root / "metrics" / "rework.json").write_text(json.dumps(out, indent=2))
    return out
