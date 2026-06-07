"""runs/**/run.json -> analysis/results.csv (one tidy row per run)."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd

REPO = Path(__file__).resolve().parents[1]
RUNS = REPO / "runs"
OUT = Path(__file__).resolve().parent / "results.csv"


def flatten(record: dict) -> dict:
    c = record["cell"]
    t = record["time"]
    b = record["budget_spent"]
    q = record["quality_rework"]
    p = record["performance"]
    f = record["functional"]
    s = record["static_quality"]
    conf = record["conformance"]
    row = {
        "run_id": record["run_id"],
        "domain": c["domain"], "arm": c["arm"], "model": c["model"],
        "task": c["task"], "rep": c["rep"],
        "budget_exhausted": record["budget"]["exhausted"],
        # TIME
        "wall_seconds": t["wall_seconds"],
        "active_agent_seconds": t["active_agent_seconds"],
        "seconds_to_first_green_build": t["seconds_to_first_green_build"],
        "seconds_to_all_green": t["seconds_to_all_green"],
        # BUDGET
        "tokens_in": b["tokens_in"], "tokens_out": b["tokens_out"],
        "turns": b["turns"], "tool_invocations": b["tool_invocations"],
        "cost_usd": b["cost_usd"], "cost_source": b["cost_source"],
        # QUALITY (rework)
        "iterations_total": q["iterations_total"],
        "rework_iterations": q["iterations_after_first_test_run"],
        "rework_loc": q["loc_churned_after_first_test_run"],
        "rework_files": q["files_touched_in_rework"],
        "rework_ratio": q["rework_ratio"],
        "tests_failing_at_end": q["tests_failing_at_end"],
        # PERFORMANCE
        "perf_measured": p["measured"],
        "p95_ms": p["overall_p95_ms"], "rps": p["overall_rps"],
        "error_rate": p["overall_error_rate"],
        "mem_app_mb_peak": p["mem_app_rss_mb_peak"],
        "mem_jvm_heap_mb": p["mem_jvm_heap_mb"],
        "mem_total_stack_mb": p["mem_total_stack_mb"],
        # functional
        "builds": f["builds_cleanly"], "boots": f["boots_cleanly"],
        "acceptance_pass_rate": f["acceptance_pass_rate"],
        "variant_pass_rate": f["variant_pass_rate"],
        # static quality
        "loc_java": s.get("loc_java"), "classes": s.get("classes"),
        "cbo_mean": (s.get("cbo") or {}).get("mean"),
        "wmc_mean": (s.get("wmc") or {}).get("mean"),
        "lcom_mean": (s.get("lcom") or {}).get("mean"),
        "pmd_per_kloc": s.get("pmd_violations_per_kloc"),
        # conformance
        "conformance_violations": conf.get("total_violations"),
        "conformance_inconclusive": conf.get("inconclusive_rules"),
        "strict_confinement_would_fail": conf.get("strict_confinement_would_fail"),
    }
    if record.get("t2"):
        row.update(t2_files_touched=record["t2"]["files_touched"],
                   t2_diff_added=record["t2"]["diff_loc_added"],
                   t2_diff_deleted=record["t2"]["diff_loc_deleted"])
    return row


def main() -> None:
    rows = []
    for rj in sorted(RUNS.glob("*/run.json")):
        rows.append(flatten(json.loads(rj.read_text())))
    df = pd.DataFrame(rows)
    OUT.parent.mkdir(exist_ok=True)
    df.to_csv(OUT, index=False)
    print(f"{len(df)} runs -> {OUT}")
    if len(df):
        print(df.groupby(["domain", "arm", "model", "task"])[
            ["acceptance_pass_rate", "cost_usd", "wall_seconds"]].mean(numeric_only=True))


if __name__ == "__main__":
    main()
