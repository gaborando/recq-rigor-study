"""The run record: the single source of truth for what a run produces.

Everything the analysis pipeline consumes is validated through RunRecord.
A failed run (no build / no boot / budget exhausted) is still a valid record —
failure is data; absent measurements are explicit `None`s with a reason.
"""

from __future__ import annotations

from datetime import datetime
from enum import StrEnum
from typing import Optional

from pydantic import BaseModel, Field


class Task(StrEnum):
    T1 = "t1"  # greenfield build to spec
    T2 = "t2"  # evolution: add T2_FEATURE to the same cell's T1 output


class CostSource(StrEnum):
    REPORTED = "reported"      # the agent CLI itself reported cost/usage
    COMPUTED = "computed"      # tokens from CLI usage x pinned price table
    ESTIMATED = "estimated"    # tokenizer estimate over transcript (flagged, last resort)


class Topology(StrEnum):
    SINGLE = "single"   # one deployable
    MICRO = "micro"     # independent services, database-per-service


class Cell(BaseModel):
    """The experimental unit identity."""
    domain: str                 # e.g. "order-inventory"
    arm: str                    # registry key in config/arms.yaml
    model: str                  # registry key in config/models.yaml
    model_id: str               # the exact pinned model identifier
    task: Task
    topology: Topology = Topology.SINGLE
    rep: int = Field(ge=1)
    seed: Optional[int] = None  # sampling seed when the CLI supports one


class EnvInfo(BaseModel):
    harness_git_sha: str
    agent_cli: str              # e.g. "claude"
    agent_cli_version: str
    docker_image_digests: dict[str, str]
    tool_versions: dict[str, str]      # ck, pmd, cloc, jdk, maven, k6
    host: str                   # platform summary (no secrets)


class Budget(BaseModel):
    """Caps applied to the run — identical across arms within a cell."""
    max_wall_minutes: int
    max_turns: int
    max_tokens: int
    exhausted: bool = False
    exhausted_reason: Optional[str] = None   # wall|turns|tokens


class TimeKpi(BaseModel):
    """TIME: time spent realizing the project."""
    started_at: datetime
    finished_at: datetime
    wall_seconds: float
    active_agent_seconds: Optional[float] = None
    seconds_to_first_green_build: Optional[float] = None
    seconds_to_all_green: Optional[float] = None   # None = never reached green


class BudgetKpi(BaseModel):
    """BUDGET: cost of the solution in LLM resources."""
    tokens_in: Optional[int] = None
    tokens_out: Optional[int] = None
    cache_tokens: Optional[int] = None
    turns: Optional[int] = None
    tool_invocations: Optional[int] = None
    cost_usd: Optional[float] = None
    cost_source: Optional[CostSource] = None


class QualityKpi(BaseModel):
    """QUALITY: rework — changes to the code from the original design due to
    test failures. 'Original design' = workspace tree at the first full
    acceptance-suite execution; everything after is rework."""
    iterations_total: int
    iterations_after_first_test_run: Optional[int] = None
    loc_churned_after_first_test_run: Optional[int] = None
    files_touched_in_rework: Optional[int] = None
    rework_ratio: Optional[float] = None     # churned LOC / final LOC
    tests_failing_at_end: Optional[int] = None


class EndpointPerf(BaseModel):
    endpoint: str
    p50_ms: float
    p95_ms: float
    p99_ms: float
    rps: float
    error_rate: float


class PerformanceKpi(BaseModel):
    """PERFORMANCE: runtime behavior under the fixed k6 workload."""
    measured: bool = False
    skip_reason: Optional[str] = None        # e.g. "app never booted"
    endpoints: list[EndpointPerf] = []
    overall_p95_ms: Optional[float] = None
    overall_rps: Optional[float] = None
    overall_error_rate: Optional[float] = None
    mem_app_rss_mb_peak: Optional[float] = None
    mem_app_rss_mb_avg: Optional[float] = None
    mem_jvm_heap_mb: Optional[float] = None
    mem_total_stack_mb: Optional[float] = None   # app + required infra (fairness: both views)


class Functional(BaseModel):
    builds_cleanly: bool
    boots_cleanly: bool
    boot_flaky: bool = False
    acceptance_pass_rate: Optional[float] = None   # 0..1 on the frozen TDD suite
    acceptance_passed: Optional[int] = None
    acceptance_total: Optional[int] = None
    variant_pass_rate: Optional[float] = None      # anti-gaming re-grade


class CkAggregate(BaseModel):
    mean: float
    max: float


class StaticQuality(BaseModel):
    measured: bool = False
    loc_java: Optional[int] = None
    files_java: Optional[int] = None
    classes: Optional[int] = None
    cbo: Optional[CkAggregate] = None
    wmc: Optional[CkAggregate] = None
    lcom: Optional[CkAggregate] = None
    rfc: Optional[CkAggregate] = None
    dit: Optional[CkAggregate] = None
    pmd_violations: Optional[int] = None
    pmd_violations_per_kloc: Optional[float] = None


class RuleResult(StrEnum):
    PASS = "pass"
    VIOLATION = "violation"
    INCONCLUSIVE = "inconclusive"   # slice empty/missing — itself a signal


class ConformanceRule(BaseModel):
    rule: str
    result: RuleResult
    violations: int = 0
    detail: Optional[str] = None


class Conformance(BaseModel):
    measured: bool = False
    rules: list[ConformanceRule] = []
    total_violations: Optional[int] = None
    inconclusive_rules: Optional[int] = None
    strict_confinement_would_fail: Optional[bool] = None   # arm A only


class ResilienceScenario(BaseModel):
    name: str                                # crash_mid_burst | full_restart | saga_under_downed_dep
    passed: Optional[bool] = None
    recovery_seconds: Optional[float] = None
    detail: Optional[str] = None


class Resilience(BaseModel):
    """Micro-topology chaos outcomes (None/empty for single topology)."""
    measured: bool = False
    skip_reason: Optional[str] = None
    scenarios: list[ResilienceScenario] = []


class T2Info(BaseModel):
    seeded_from_run_id: Optional[str] = None
    skipped_t1_failed: bool = False
    files_touched: Optional[int] = None
    diff_loc_added: Optional[int] = None
    diff_loc_deleted: Optional[int] = None


class RunRecord(BaseModel):
    run_id: str
    schema_version: int = 1
    cell: Cell
    env: EnvInfo
    budget: Budget
    time: TimeKpi
    budget_spent: BudgetKpi
    quality_rework: QualityKpi
    performance: PerformanceKpi
    functional: Functional
    static_quality: StaticQuality
    conformance: Conformance
    resilience: Resilience = Resilience()
    t2: Optional[T2Info] = None
    notes: list[str] = []
