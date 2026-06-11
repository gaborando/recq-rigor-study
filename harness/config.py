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
    topology: str = "single"   # single | micro


def domain_cfg(domain: str) -> dict:
    return load("domains")["domains"][domain]


def domain_services(domain: str) -> list[str]:
    """Ordered micro-topology service list; [0] is the stateless edge gateway."""
    return domain_cfg(domain)["micro_services"]


def arm_cfg(arm_key: str, topology: str, domain: str = "order-inventory") -> dict:
    """Resolve an arm's config for a (topology, domain): base keys overlaid by
    topologies.<topology>. For micro, skeleton/runtime_compose are resolved
    per-domain (`skeletons/<arm>_micro_<domain>`, `runtime/<arm>_micro_<domain>`)."""
    arm = dict(load("arms")["arms"][arm_key])
    topo = dict(arm.pop("topologies", {}).get(topology, {}))
    if topology == "micro":
        topo["skeleton"] = f"skeletons/{arm_key}_micro_{domain}"
        topo["runtime_compose"] = f"runtime/{arm_key}_micro_{domain}/docker-compose.yaml"
    arm.update(topo)
    return arm


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
                                      rep, budget, perf, c.get("topology", "single")))
    else:  # full cartesian product
        for topology in p.get("topologies", ["single"]):
            for domain in p["domains"]:
                for arm in p["arms"]:
                    for model in p["models"]:
                        for task in p["tasks"]:
                            for rep in range(1, reps + 1):
                                cells.append(CellSpec(domain, arm, model, task,
                                                      rep, budget, perf, topology))
    return cells
