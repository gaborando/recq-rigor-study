"""PERFORMANCE-adjacent: micro-topology chaos scenarios.

Drives the workspace's kill/restart scripts between phases and re-runs the
chaos pytest module (which holds the invariant assertions). Single topology
returns measured=False.

Scenarios (the chaos pytest file owns the load + invariant checks; this driver
owns the fault injection ordering):
  crash_mid_burst        — restart-service.sh inventory mid-test
  full_restart           — down.sh + up.sh after a burst
  saga_under_downed_dep  — stop customers, then bring back; order resolves once
"""

from __future__ import annotations

import json
import os
import subprocess
import time
from pathlib import Path

from ..config import REPO
from ..workspace import Workspace, sh


def _run_chaos_pytest(w: Workspace, scenario: str, report: Path) -> dict:
    """Run a single chaos scenario test; the test coordinates with fault
    injection via the CHAOS_SCENARIO env var + helper hooks in conftest."""
    env = {**os.environ,
           "BASE_URL": f"http://localhost:{w.app_port}",
           "TASK": w.cell.task, "TOPOLOGY": "micro",
           "CHAOS_SCENARIO": scenario,
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


def measure(w: Workspace) -> dict:
    if w.cell.topology != "micro":
        out = {"measured": False, "skip_reason": "single topology", "scenarios": []}
        (w.root / "metrics" / "resilience.json").write_text(json.dumps(out, indent=2))
        return out

    chaos_file = REPO / "acceptance" / w.cell.domain / "test_chaos.py"
    if not chaos_file.exists():
        out = {"measured": False, "skip_reason": "no chaos suite for domain", "scenarios": []}
        (w.root / "metrics" / "resilience.json").write_text(json.dumps(out, indent=2))
        return out

    scenarios = ["crash_mid_burst", "full_restart", "saga_under_downed_dep"]
    results = []
    for sc in scenarios:
        t0 = time.monotonic()
        r = _run_chaos_pytest(w, sc, w.root / "reports" / f"chaos_{sc}.json")
        results.append({"name": sc, "passed": r["passed"],
                        "recovery_seconds": round(time.monotonic() - t0, 1),
                        "detail": r["detail"]})
        # always restore full service set between scenarios
        sh(["bash", "scripts/up.sh"], cwd=w.dir, check=False, timeout=600)

    out = {"measured": True, "skip_reason": None, "scenarios": results}
    (w.root / "metrics" / "resilience.json").write_text(json.dumps(out, indent=2))
    return out
