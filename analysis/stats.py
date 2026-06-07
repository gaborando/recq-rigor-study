"""Nonparametric arm comparisons: Mann-Whitney U + Cliff's delta per KPI metric.

For each (domain, task, metric): pairwise arm comparisons. With small N these
are descriptive, not confirmatory — the paper reports effect sizes + raw data.
"""

from __future__ import annotations

from itertools import combinations
from pathlib import Path

import pandas as pd
from scipy.stats import mannwhitneyu

RESULTS = Path(__file__).resolve().parent / "results.csv"
OUT = Path(__file__).resolve().parent / "stats.csv"

KPI_METRICS = [
    # TIME
    "wall_seconds", "seconds_to_all_green",
    # BUDGET
    "tokens_out", "cost_usd", "turns",
    # QUALITY
    "rework_iterations", "rework_loc", "rework_ratio", "tests_failing_at_end",
    # PERFORMANCE
    "p95_ms", "rps", "mem_app_mb_peak", "mem_total_stack_mb",
    # secondary
    "acceptance_pass_rate", "variant_pass_rate", "loc_java", "cbo_mean",
    "pmd_per_kloc", "conformance_violations",
]


def cliffs_delta(a: list[float], b: list[float]) -> float:
    gt = sum(1 for x in a for y in b if x > y)
    lt = sum(1 for x in a for y in b if x < y)
    return (gt - lt) / (len(a) * len(b)) if a and b else float("nan")


def main() -> None:
    df = pd.read_csv(RESULTS)
    rows = []
    for (domain, task), g in df.groupby(["domain", "task"]):
        arms = sorted(g["arm"].unique())
        for metric in KPI_METRICS:
            if metric not in g.columns:
                continue
            for a1, a2 in combinations(arms, 2):
                x = g[g.arm == a1][metric].dropna().tolist()
                y = g[g.arm == a2][metric].dropna().tolist()
                if len(x) < 1 or len(y) < 1:
                    continue
                try:
                    u, p = mannwhitneyu(x, y, alternative="two-sided")
                except ValueError:
                    u, p = float("nan"), float("nan")
                rows.append({
                    "domain": domain, "task": task, "metric": metric,
                    "arm_a": a1, "arm_b": a2, "n_a": len(x), "n_b": len(y),
                    "median_a": pd.Series(x).median(), "median_b": pd.Series(y).median(),
                    "mannwhitney_u": u, "p_value": p,
                    "cliffs_delta": round(cliffs_delta(x, y), 3),
                })
    out = pd.DataFrame(rows)
    out.to_csv(OUT, index=False)
    print(f"{len(out)} comparisons -> {OUT}")


if __name__ == "__main__":
    main()
