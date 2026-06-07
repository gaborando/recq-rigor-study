"""results.csv -> per-KPI box plots by arm (analysis/figures/)."""

from __future__ import annotations

from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import pandas as pd

HERE = Path(__file__).resolve().parent
RESULTS = HERE / "results.csv"
OUT = HERE / "figures"

METRICS = ["wall_seconds", "cost_usd", "rework_iterations", "rework_loc",
           "p95_ms", "mem_total_stack_mb", "acceptance_pass_rate",
           "loc_java", "conformance_violations"]


def main() -> None:
    df = pd.read_csv(RESULTS)
    OUT.mkdir(exist_ok=True)
    for (domain, task), g in df.groupby(["domain", "task"]):
        for metric in METRICS:
            if metric not in g.columns or g[metric].dropna().empty:
                continue
            fig, ax = plt.subplots(figsize=(5, 3.2))
            data = [g[g.arm == a][metric].dropna() for a in sorted(g.arm.unique())]
            ax.boxplot(data, tick_labels=sorted(g.arm.unique()))
            ax.set_title(f"{metric} — {domain} {task}")
            ax.set_ylabel(metric)
            fig.tight_layout()
            name = f"{domain}__{task}__{metric}.pdf"
            fig.savefig(OUT / name)
            plt.close(fig)
            print(f"wrote figures/{name}")


if __name__ == "__main__":
    main()
