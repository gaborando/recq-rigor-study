"""Acceptance + variant suite grading (black-box, from the repo — not the workspace)."""

from __future__ import annotations

import json
import os
import subprocess
from pathlib import Path

from ..config import REPO
from ..workspace import Workspace


def _run_pytest(suite_dir: Path, base_url: str, task: str, report: Path,
                extra_env: dict | None = None) -> dict:
    env = {**os.environ, "BASE_URL": base_url, "TASK": task, **(extra_env or {})}
    cmd = ["uv", "run", "pytest", str(suite_dir), "-p", "no:cacheprovider", "-q",
           "--json-report", f"--json-report-file={report}"]
    subprocess.run(cmd, cwd=REPO, env=env, capture_output=True, text=True, timeout=3600)
    if not report.exists():
        return {"passed": 0, "total": 0, "error": "no report produced"}
    data = json.loads(report.read_text())
    s = data.get("summary", {})
    total = s.get("total", 0) - s.get("deselected", 0)
    collected = total - s.get("skipped", 0)
    return {"passed": s.get("passed", 0), "total": collected,
            "failed": s.get("failed", 0) + s.get("error", 0)}


def grade(w: Workspace) -> dict:
    base_url = f"http://localhost:{w.app_port}"
    acc = _run_pytest(REPO / "acceptance" / w.cell.domain, base_url, w.cell.task,
                      w.root / "reports" / "acceptance.json")
    var = _run_pytest(REPO / "variant" / w.cell.domain, base_url, w.cell.task,
                      w.root / "reports" / "variant.json")
    out = {
        "acceptance_passed": acc["passed"],
        "acceptance_total": acc["total"],
        "acceptance_pass_rate": (acc["passed"] / acc["total"]) if acc["total"] else None,
        "acceptance_failed": acc.get("failed"),
        "variant_pass_rate": (var["passed"] / var["total"]) if var["total"] else None,
    }
    (w.root / "metrics" / "functional.json").write_text(json.dumps(out, indent=2))
    return out
