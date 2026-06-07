"""Config loading: arms, models, matrix, versions — all from config/*.yaml."""

from __future__ import annotations

import subprocess
from dataclasses import dataclass
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[1]
CONFIG = REPO / "config"
RUNS = REPO / "runs"


def load(name: str) -> dict:
    with open(CONFIG / f"{name}.yaml") as f:
        return yaml.safe_load(f)


@dataclass(frozen=True)
class BudgetCfg:
    max_wall_minutes: int
    max_turns: int
    max_tokens: int


@dataclass(frozen=True)
class CellSpec:
    domain: str
    arm: str
    model: str
    task: str
    rep: int
    budget: BudgetCfg
    perf: dict


def harness_git_sha() -> str:
    try:
        return subprocess.run(["git", "rev-parse", "HEAD"], cwd=REPO, check=True,
                              capture_output=True, text=True).stdout.strip()
    except Exception:
        return "unknown"


def expand_profile(profile: str) -> list[CellSpec]:
    m = load("matrix")
    defaults = m["defaults"]
    p = m["profiles"][profile]
    budget = BudgetCfg(**{**defaults["budget"], **p.get("budget", {})})
    perf = {**defaults["perf"], **p.get("perf", {})}
    reps = p.get("reps", defaults.get("reps", 1))

    cells: list[CellSpec] = []
    if "cells" in p:  # explicit cell list (pilot)
        for c in p["cells"]:
            for rep in range(1, reps + 1):
                cells.append(CellSpec(c["domain"], c["arm"], c["model"], c["task"],
                                      rep, budget, perf))
    else:  # full cartesian product
        for domain in p["domains"]:
            for arm in p["arms"]:
                for model in p["models"]:
                    for task in p["tasks"]:
                        for rep in range(1, reps + 1):
                            cells.append(CellSpec(domain, arm, model, task,
                                                  rep, budget, perf))
    return cells
