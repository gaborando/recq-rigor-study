"""results.csv -> LaTeX tables for the paper (analysis/tables/)."""

from __future__ import annotations

from pathlib import Path

import pandas as pd

HERE = Path(__file__).resolve().parent
RESULTS = HERE / "results.csv"
OUT = HERE / "tables"

KPI_BLOCKS = {
    "time": ["wall_seconds", "seconds_to_all_green"],
    "budget": ["tokens_out", "turns", "cost_usd"],
    "quality": ["rework_iterations", "rework_loc", "rework_ratio", "tests_failing_at_end"],
    "performance": ["p95_ms", "rps", "mem_app_mb_peak", "mem_total_stack_mb"],
    "secondary": ["acceptance_pass_rate", "variant_pass_rate", "loc_java",
                  "cbo_mean", "pmd_per_kloc", "conformance_violations"],
}


def main() -> None:
    df = pd.read_csv(RESULTS)
    OUT.mkdir(exist_ok=True)
    for block, metrics in KPI_BLOCKS.items():
        cols = [m for m in metrics if m in df.columns]
        agg = (df.groupby(["domain", "task", "arm"])[cols]
                 .median(numeric_only=True).round(2))
        tex = agg.to_latex(
            caption=f"{block.upper()} KPIs — per-cell medians.",
            label=f"tab:{block}", escape=True)
        (OUT / f"{block}.tex").write_text(tex)
        print(f"wrote tables/{block}.tex")


if __name__ == "__main__":
    main()
