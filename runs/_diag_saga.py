#!/usr/bin/env python3
"""Diagnostic: capture WHY oi/evento's saga doesn't resume after the charge
dependency (customers) recovers.

Boots the preserved oi/evento workspace on a fresh empty store, runs ONLY the
saga_under_downed_dep chaos scenario, then snapshots the service logs BEFORE
teardown (the normal grading path rewrites orders.log via between-scenario
down/up, erasing the evidence). Greps the captured orders log for the
framework's classification of the failed ChargeCommand send (TransientConsumer
=> redeliver vs DLQ/dead-letter => permanent => saga stranded).
"""
from __future__ import annotations

import glob
import json
import re
import shutil
import sys
from pathlib import Path

from harness import build_boot
from harness.config import REPO, BudgetCfg, CellSpec, arm_cfg, load
from harness.metrics import resilience
from harness.workspace import Workspace, start_infra, teardown

CELL_GLOB = "runs/order-inventory__arm_a_evento__claude-opus*"


def main() -> None:
    run_dir = Path(sorted(glob.glob(str(REPO / CELL_GLOB)))[-1])
    rj = json.loads((run_dir / "run.json").read_text())
    c = rj["cell"]
    d = load("matrix")["defaults"]
    cell = CellSpec(c["domain"], c["arm"], c["model"], c["task"], c["rep"],
                    BudgetCfg(**d["budget"]), d["perf"])
    arm = arm_cfg(cell.arm, cell.domain)
    w = Workspace(run_id=run_dir.name, cell=cell, root=run_dir,
                  dir=run_dir / "workspace",
                  compose_file=REPO / arm["runtime_compose"])
    diag = run_dir / "logs" / "saga_diag"
    diag.mkdir(parents=True, exist_ok=True)
    try:
        w.compose("down", "-v", check=False)
        start_infra(w)
        print(f"infra up (app port {w.app_port})", flush=True)
        assert build_boot.final_build(w, run_dir / "logs" / "build-diag.log"), "build failed"
        boots, _f, _p = build_boot.boot(w, run_dir / "logs" / "boot-diag.log")
        assert boots, "boot failed"
        print("running saga_under_downed_dep ...", flush=True)
        r = resilience._run_chaos_pytest(
            w, "saga_under_downed_dep", run_dir / "reports" / "chaos_saga_diag.json")
        print(f"scenario result: {r}", flush=True)
        # snapshot the service logs NOW, before teardown rewrites them
        for name in ("orders.log", "customers.log", "inventory.log", "edge.log"):
            src = w.dir / name
            if src.exists():
                shutil.copy(src, diag / name)
        print(f"logs captured to {diag}", flush=True)
    finally:
        teardown(w)

    olog = diag / "orders.log"
    if not olog.exists():
        print("no orders.log captured", flush=True)
        return
    text = olog.read_text(errors="ignore")
    pat = re.compile(
        r"(TransientConsumer|dead.?letter|DeadEvent|DLQ|retry|exhaust|"
        r"ChargeCommand|no handler|No handler|HandlerNotFound|not.*registered|"
        r"unavailable|TimeoutException|ExecutionException|SendFailed|"
        r"Saga|charge)", re.I)
    hits = [ln for ln in text.splitlines() if pat.search(ln)]
    print(f"\n===== {len(hits)} relevant orders.log lines =====", flush=True)
    for ln in hits[-60:]:
        print(ln, flush=True)


if __name__ == "__main__":
    main()
