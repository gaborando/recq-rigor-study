"""Architecture conformance: ArchUnit rules + (arm A) strictConfinement probe."""

from __future__ import annotations

import json

from ..config import REPO, load
from ..workspace import Workspace, sh


def compute(w: Workspace, strict_confinement_would_fail: bool | None) -> dict:
    arm_key = load("arms")["arms"][w.cell.arm]["archunit_arm"]
    # single: one module's classes. micro: import the whole reactor tree (the
    # ClassFileImporter picks up every */target/classes across all services).
    classes = w.dir if w.cell.topology == "micro" else w.dir / "target" / "classes"
    has_classes = (w.cell.topology == "micro" and any(w.dir.rglob("target/classes"))) \
        or (w.cell.topology == "single" and classes.exists())
    out: dict = {"measured": False, "rules": [],
                 "strict_confinement_would_fail": strict_confinement_would_fail}
    if has_classes:
        r = sh(["mvn", "-q", "compile", "exec:java",
                f"-Dconformance.classes={classes}", f"-Dconformance.arm={arm_key}"],
               cwd=REPO / "archunit", check=False, timeout=600)
        (w.root / "logs" / "conformance.log").write_text(r.stdout + "\n" + r.stderr)
        # the JSON object is the last stdout line starting with '{'
        for line in reversed(r.stdout.strip().splitlines()):
            if line.startswith("{"):
                try:
                    data = json.loads(line)
                    out["measured"] = True
                    out["rules"] = data.get("rules", [])
                except json.JSONDecodeError:
                    pass
                break
    if out["measured"]:
        out["total_violations"] = sum(r["violations"] for r in out["rules"])
        out["inconclusive_rules"] = sum(1 for r in out["rules"]
                                        if r["result"] == "inconclusive")
    (w.root / "metrics" / "conformance.json").write_text(json.dumps(out, indent=2))
    return out
