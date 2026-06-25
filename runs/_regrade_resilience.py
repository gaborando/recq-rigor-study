#!/usr/bin/env python3
"""Re-grade resilience on a fresh, perf-free runtime (decouple chaos from the
perf-phase projector saturation).

The grading pipeline runs perf (24k k6 writes) immediately before resilience on
the SAME runtime. For the broker arms (Evento/Axon) that saturates the
single-consumer projector, so the chaos phase then fails LIVENESS checks
("timed out waiting for N decisions") that are about throughput, not resilience.
Because the event store is durable, a mere reboot replays the perf backlog and
stays saturated — so the only clean isolation is to run chaos on an EMPTY store.

This re-grader reuses each run's PRESERVED workspace (the agent's final code; the
DB volumes were torn down so a fresh `compose up` starts empty), rebuilds, boots,
and runs ONLY resilience.measure. Empty event store => projector at head => not
perf-saturated. The chaos tests do their own small setup, so an empty store is
correct. No agent run (same generated code => comparable; no model cost / no
session limit).

Old artifacts are snapshotted to *_pre_decouple before being overwritten, and the
run.json `resilience` block is patched in place with a note.

Usage:  uv run python runs/_regrade_resilience.py [run_dir ...]
        (default: all runs/*claude-opus*t1* cells)
"""
from __future__ import annotations

import datetime
import glob
import json
import shutil
import sys
from pathlib import Path

from harness import build_boot
from harness.config import REPO, RUNS, BudgetCfg, CellSpec, arm_cfg, load
from harness.metrics import resilience
from harness.workspace import Workspace, start_infra, teardown

STAMP = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def log(msg: str) -> None:
    print(f"[{datetime.datetime.now():%Y-%m-%d %H:%M:%S}] {msg}", flush=True)


def make_ws(run_dir: Path) -> tuple[Workspace, dict]:
    rj = json.loads((run_dir / "run.json").read_text())
    c = rj["cell"]
    d = load("matrix")["defaults"]
    cell = CellSpec(c["domain"], c["arm"], c["model"], c["task"], c["rep"],
                    BudgetCfg(**d["budget"]), d["perf"])
    arm = arm_cfg(cell.arm, cell.domain)
    w = Workspace(run_id=run_dir.name, cell=cell, root=run_dir,
                  dir=run_dir / "workspace",
                  compose_file=REPO / arm["runtime_compose"])
    return w, rj


def snapshot_old(run_dir: Path, rj: dict) -> None:
    """Preserve the pre-decouple resilience artifacts for audit (once — never
    clobber an existing snapshot of the true original on a re-grade re-run)."""
    if (run_dir / "metrics" / "resilience_pre_decouple.json").exists():
        return
    old = {"resilience_run_json_block": rj.get("resilience")}
    rjson = run_dir / "metrics" / "resilience.json"
    if rjson.exists():
        old["resilience_json"] = json.loads(rjson.read_text())
    (run_dir / "metrics" / "resilience_pre_decouple.json").write_text(
        json.dumps(old, indent=2))
    for rep in (run_dir / "reports").glob("chaos_*.json"):
        shutil.copy(rep, rep.with_name(rep.stem + "_pre_decouple.json"))


def regrade(run_dir: Path) -> dict:
    w, rj = make_ws(run_dir)
    if not w.dir.exists():
        log(f"  SKIP {run_dir.name}: no preserved workspace")
        return {"measured": False, "skip_reason": "no workspace", "scenarios": []}
    log(f"REGRADE {run_dir.name}")
    snapshot_old(run_dir, rj)
    try:
        w.compose("down", "-v", check=False)        # ensure no stale state
        start_infra(w)                               # fresh empty DBs
        log(f"  infra up (app port {w.app_port})")
        builds = build_boot.final_build(w, run_dir / "logs" / "build-regrade.log")
        boots = False
        if builds:
            boots, _flaky, _pids = build_boot.boot(
                w, run_dir / "logs" / "boot-regrade.log")
        if not boots:
            log(f"  build={builds} boot={boots} — cannot re-grade")
            return {"measured": False,
                    "skip_reason": f"regrade build={builds} boot={boots}",
                    "scenarios": []}
        resil = resilience.measure(w)                # chaos on a non-saturated store
        n = sum(1 for s in resil.get("scenarios", []) if s["passed"])
        tot = len(resil.get("scenarios", []))
        log(f"  resilience {n}/{tot}: " + " ".join(
            f"{s['name'].split('_')[0]}:{'P' if s['passed'] else 'F'}"
            f"({s.get('recovery_seconds')}s)" for s in resil.get("scenarios", [])))
        return resil
    finally:
        teardown(w)


def patch_run_json(run_dir: Path, resil: dict) -> None:
    rj = json.loads((run_dir / "run.json").read_text())
    rj["resilience"] = {k: resil[k] for k in ("measured", "skip_reason", "scenarios")
                        if k in resil}
    rj.setdefault("notes", []).append(
        f"resilience re-graded {STAMP} on a fresh perf-free runtime (empty event "
        f"store; decoupled from the perf-phase projector saturation). Original "
        f"resilience preserved in metrics/resilience_pre_decouple.json.")
    (run_dir / "run.json").write_text(json.dumps(rj, indent=2))


def main() -> None:
    args = sys.argv[1:]
    if args:
        dirs = [Path(a) if Path(a).is_absolute() else (RUNS / a) for a in args]
    else:
        dirs = sorted(Path(p) for p in glob.glob(str(RUNS / "*claude-opus*t1*"))
                      if (Path(p) / "run.json").exists())
    log(f"re-grading resilience for {len(dirs)} cell(s) on fresh perf-free runtimes")
    for run_dir in dirs:
        try:
            resil = regrade(run_dir)
            patch_run_json(run_dir, resil)
        except Exception as e:                       # one cell failing != abort all
            log(f"  ERROR {run_dir.name}: {e!r}")
    log("REGRADE COMPLETE")


if __name__ == "__main__":
    main()
