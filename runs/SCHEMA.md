# Run record schema

Each run persists under `runs/<run_id>/` where

```
run_id = {domain}__{arm}__{model}__{task}__rep{N}__{utc_compact_timestamp}
e.g.     order-inventory__arm_a_evento__claude-sonnet__t1__rep1__20260607T141502Z
```

## Directory layout

```
runs/<run_id>/
├── run.json                  # the validated record (harness/schema.py is the source of truth)
├── workspace/                # final generated project, WITH .git history:
│                             #   one commit per agent iteration (= QUALITY/rework evidence)
├── agent/
│   ├── transcript.jsonl      # raw agent CLI event stream
│   └── stdout.log
├── metrics/
│   ├── functional.json       # acceptance + variant suite pass rates, per-test results
│   ├── effort.json           # TIME + BUDGET KPIs
│   ├── rework.json           # QUALITY KPI (churn after first test execution)
│   ├── perf.json             # PERFORMANCE KPI (k6 + memory sampling)
│   ├── static.json           # cloc / CK / PMD
│   ├── conformance.json      # ArchUnit per-rule pass|violation|inconclusive (+ strictConfinement probe for arm A)
│   └── change_impact.json    # T2 only: diff vs the seeding T1 tree
├── reports/
│   ├── acceptance.json       # pytest --json-report output
│   ├── variant.json
│   └── k6-summary.json
└── logs/
    ├── build.log
    └── boot.log
```

## run.json (summary of key fields — see `harness/schema.py` for the full model)

| Block | Fields |
|---|---|
| `cell` | domain, arm, model (registry key + exact model_id), task, rep, seed |
| `env` | harness git SHA, agent CLI version, docker image digests, tool versions, host info |
| `budget` | the caps applied (identical across arms within a cell) + exhausted: bool |
| `time` | started_at, finished_at, wall_seconds, active_agent_seconds, seconds_to_first_green_build, seconds_to_all_green (null if never) |
| `budget_spent` | tokens_in, tokens_out, cache_tokens, turns, tool_invocations, cost_usd, cost_source: reported\|computed\|estimated |
| `quality_rework` | iterations_total, iterations_after_first_test_run, loc_churned_after_first_test_run, files_touched_in_rework, rework_ratio, tests_failing_at_end |
| `performance` | per-endpoint p50/p95/p99 ms, throughput rps, error_rate, mem_app_rss_mb (peak/avg), mem_jvm_heap_mb, mem_total_stack_mb |
| `functional` | acceptance_pass_rate, variant_pass_rate, builds_cleanly, boots_cleanly, boot_flaky |
| `static_quality` | loc, classes, ck aggregates (cbo/wmc/lcom/rfc/dit mean+max), pmd_violations |
| `conformance` | per-rule results, violations count, inconclusive count, strict_confinement_would_fail (arm A) |
| `t2` | seeded_from_run_id, files_touched, diff_loc_added, diff_loc_deleted, skipped_t1_failed |

Notes:
- A run that fails (no build, boot, or budget exhausted) is still a VALID record —
  failure is data. Missing measurements are explicit nulls with a `reason`.
- The variant suite (anti-gaming) is never copied into `workspace/`; `make leakcheck`
  asserts this over all persisted runs.
