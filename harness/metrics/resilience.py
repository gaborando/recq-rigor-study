"""Resilience: distributed-systems chaos scenarios against the multi-service system.

Drives the workspace's kill/restart scripts between phases and re-runs the
chaos pytest module (which holds the invariant assertions). The crash/downed
targets come from the domain registry (config/domains.yaml).

Scenarios (the chaos pytest file owns the load + invariant checks; this driver
owns the fault injection ordering):
  crash_mid_burst        — restart CRASH_TARGET service mid-test
  full_restart           — down.sh + up.sh after a burst (durability)
  saga_under_downed_dep  — stop DOWNED_DEP, then bring back; resolves exactly once
"""

from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path

from ..config import REPO, domain_cfg
from ..workspace import Workspace, sh


def _run_chaos_pytest(w: Workspace, scenario: str, report: Path) -> dict:
    """Run a single chaos scenario test; the test coordinates with fault
    injection via the CHAOS_SCENARIO env var + helper hooks in conftest."""
    chaos = domain_cfg(w.cell.domain)["chaos"]
    env = {**os.environ,
           "BASE_URL": f"http://localhost:{w.app_port}",
           "TASK": w.cell.task,
           "CHAOS_SCENARIO": scenario,
           "CRASH_TARGET": chaos["crash_target"],
           "DOWNED_DEP": chaos["downed_dep"],
           "WORKSPACE_DIR": str(w.dir)}
    cmd = ["uv", "run", "pytest",
           str(REPO / "acceptance" / w.cell.domain / "test_chaos.py"),
           "-p", "no:cacheprovider", "-q", "-k", scenario,
           "--json-report", f"--json-report-file={report}"]
    subprocess.run(cmd, cwd=REPO, env=env, capture_output=True, text=True, timeout=1800)
    if not report.exists():
        return {"passed": False, "detail": "no report"}
    data = json.loads(report.read_text())
    s = data.get("summary", {})
    return {"passed": s.get("failed", 0) == 0 and s.get("passed", 0) > 0,
            "detail": f"{s.get('passed', 0)} passed, {s.get('failed', 0)} failed"}


# The distributed chaos catalogue (all run against the multi-service system).
SCENARIOS = ["crash_mid_burst", "full_restart", "saga_under_downed_dep"]


def _restore(w: Workspace) -> None:
    """Bring the system back to a full healthy state between scenarios.
    Require full bundle-readiness (not just health UP) so the next scenario's
    setup can't hit a not-yet-registered broker bundle."""
    sh(["bash", "scripts/up.sh"], cwd=w.dir, check=False, timeout=600,
       env={**os.environ, "READY_REQUIRE_BUNDLE": "1"})


def measure(w: Workspace) -> dict:
    chaos_file = REPO / "acceptance" / w.cell.domain / "test_chaos.py"
    if not chaos_file.exists():
        out = {"measured": False, "skip_reason": "no chaos suite for domain", "scenarios": []}
        (w.root / "metrics" / "resilience.json").write_text(json.dumps(out, indent=2))
        return out

    scenarios = SCENARIOS
    results = []
    for sc in scenarios:
        t0 = time.monotonic()
        r = _run_chaos_pytest(w, sc, w.root / "reports" / f"chaos_{sc}.json")
        results.append({"name": sc, "passed": r["passed"],
                        "recovery_seconds": round(time.monotonic() - t0, 1),
                        "detail": r["detail"]})
        _restore(w)

    out = {"measured": True, "skip_reason": None, "scenarios": results}
    (w.root / "metrics" / "resilience.json").write_text(json.dumps(out, indent=2))
    return out
