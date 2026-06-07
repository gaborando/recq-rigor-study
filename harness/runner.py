"""One run = the full lifecycle: provision -> agent -> grade -> measure -> persist."""

from __future__ import annotations

import json
import platform
import time
from datetime import datetime, timezone

from . import build_boot
from .agents.claude_code import get_adapter
from .config import CellSpec, harness_git_sha, load
from .metrics import change_impact, conformance, effort, functional, perf, rework, static_quality
from .prompt import build_prompt
from .schema import (Budget, BudgetKpi, Cell, Conformance, EnvInfo, Functional,
                     PerformanceKpi, QualityKpi, RunRecord, StaticQuality, T2Info, TimeKpi)
from .workspace import Workspace, provision, reset_infra, start_infra, teardown


def run_cell(cell: CellSpec) -> RunRecord:
    models = load("models")["models"]
    model_cfg = models[cell.model]
    adapter = get_adapter(model_cfg["adapter"])

    w = provision(cell)
    print(f"[{w.run_id}] provisioned at {w.dir}")
    started_at = datetime.now(timezone.utc)
    started_ts = time.time()
    seeded_from = None
    notes: list[str] = []

    try:
        start_infra(w)
        print(f"[{w.run_id}] infra up (app port {w.app_port})")

        # ---- the agent develops ----
        agent_res = adapter.run(
            workspace=w.dir,
            prompt=build_prompt(cell),
            budget=cell.budget,
            model_id=model_cfg["model_id"],
            transcript_path=w.root / "agent" / "transcript.jsonl",
            stdout_path=w.root / "agent" / "stdout.log",
            env=w.env,
        )
        print(f"[{w.run_id}] agent done: exit={agent_res.exit_code} "
              f"turns={agent_res.turns} cost=${agent_res.cost_usd} "
              f"killed={agent_res.killed_reason}")

        # ---- grading on a FRESH runtime state ----
        build_boot.stop(w)
        reset_infra(w)
        builds = build_boot.final_build(w, w.root / "logs" / "build.log")
        boots, flaky, app_pid = (False, False, None)
        if builds:
            boots, flaky, app_pid = build_boot.boot(w, w.root / "logs" / "boot.log")

        func = {"acceptance_passed": None, "acceptance_total": None,
                "acceptance_pass_rate": None, "acceptance_failed": None,
                "variant_pass_rate": None}
        perf_out: dict = {"measured": False, "skip_reason": "app never booted"}
        if boots:
            func = functional.grade(w)
            perf_out = perf.measure(w, app_pid)
        strict = build_boot.strict_confinement_probe(w, w.root / "logs" / "boot.log") \
            if boots else None

        # ---- effort / rework / static / conformance ----
        eff = effort.compute(w, agent_res, started_ts)
        rw = rework.compute(w, eff.get("first_test_ts"), func.get("acceptance_failed"))
        sq = static_quality.compute(w)
        conf = conformance.compute(w, strict)
        ci = change_impact.compute(w, seeded_from)

        finished_at = datetime.now(timezone.utc)
        budget_exhausted = agent_res.killed_reason is not None or \
            (agent_res.extra.get("result_subtype") == "error_max_turns")

        record = RunRecord(
            run_id=w.run_id,
            cell=Cell(domain=cell.domain, arm=cell.arm, model=cell.model,
                      model_id=model_cfg["model_id"], task=cell.task, rep=cell.rep),
            env=EnvInfo(
                harness_git_sha=harness_git_sha(),
                agent_cli=model_cfg["adapter"],
                agent_cli_version=agent_res.cli_version,
                docker_image_digests={k: v["ref"] for k, v in
                                      load("versions.lock")["docker_images"].items()},
                tool_versions={"python": platform.python_version()},
                host=f"{platform.system()} {platform.machine()}",
            ),
            budget=Budget(max_wall_minutes=cell.budget.max_wall_minutes,
                          max_turns=cell.budget.max_turns,
                          max_tokens=cell.budget.max_tokens,
                          exhausted=budget_exhausted,
                          exhausted_reason=agent_res.killed_reason or
                          ("turns" if budget_exhausted else None)),
            time=TimeKpi(started_at=started_at, finished_at=finished_at,
                         wall_seconds=agent_res.wall_seconds,
                         active_agent_seconds=agent_res.active_agent_seconds,
                         seconds_to_first_green_build=eff.get("seconds_to_first_green_build"),
                         seconds_to_all_green=eff.get("seconds_to_all_green")),
            budget_spent=BudgetKpi(tokens_in=agent_res.tokens_in,
                                   tokens_out=agent_res.tokens_out,
                                   cache_tokens=agent_res.cache_tokens,
                                   turns=agent_res.turns,
                                   tool_invocations=agent_res.tool_invocations,
                                   cost_usd=agent_res.cost_usd,
                                   cost_source=agent_res.cost_source),
            quality_rework=QualityKpi(
                iterations_total=rw["iterations_total"],
                iterations_after_first_test_run=rw["iterations_after_first_test_run"],
                loc_churned_after_first_test_run=rw["loc_churned_after_first_test_run"],
                files_touched_in_rework=rw["files_touched_in_rework"],
                rework_ratio=rw["rework_ratio"],
                tests_failing_at_end=rw["tests_failing_at_end"]),
            performance=PerformanceKpi(**{k: v for k, v in perf_out.items()
                                          if k in PerformanceKpi.model_fields}),
            functional=Functional(builds_cleanly=builds, boots_cleanly=boots,
                                  boot_flaky=flaky,
                                  acceptance_pass_rate=func.get("acceptance_pass_rate"),
                                  acceptance_passed=func.get("acceptance_passed"),
                                  acceptance_total=func.get("acceptance_total"),
                                  variant_pass_rate=func.get("variant_pass_rate")),
            static_quality=StaticQuality(**{k: v for k, v in sq.items()
                                            if k in StaticQuality.model_fields}),
            conformance=Conformance(**{k: v for k, v in conf.items()
                                       if k in Conformance.model_fields}),
            t2=T2Info(**ci) if cell.task == "t2" else None,
            notes=notes,
        )
        (w.root / "run.json").write_text(record.model_dump_json(indent=2))
        print(f"[{w.run_id}] persisted run.json "
              f"(acceptance {func.get('acceptance_passed')}/{func.get('acceptance_total')})")
        return record
    finally:
        build_boot.stop(w)
        teardown(w)
        # preserve the per-iteration history as a single replayable bundle so
        # the outer repo can track it (an inner .git would be an opaque gitlink)
        inner_git = w.dir / ".git"
        if inner_git.exists():
            import shutil as _shutil
            import subprocess as _sp
            _sp.run(["git", "bundle", "create",
                     str(w.root / "workspace-history.bundle"), "--all"],
                    cwd=w.dir, check=False, capture_output=True)
            _shutil.rmtree(inner_git, ignore_errors=True)
