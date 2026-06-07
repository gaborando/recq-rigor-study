# recq-rigor-study

**Does architectural rigor pay off?** A replicable, test-driven experiment that uses
**LLMs as developers** to compare the artifact quality, development effort, and runtime
behavior of the same system built under three technology constraints:

| Arm | Constraint |
|-----|-----------|
| `arm_a_evento` | [Evento Framework](https://github.com/EventoFramework/evento-framework) (RECQ architecture — enforced component model) |
| `arm_b_spring` | Plain Spring Boot — no CQRS/ES framework |
| `arm_c_axon`   | [Axon Framework](https://www.axoniq.io/) (CQRS/ES, embedded JPA event store) |

This repository is the research artifact for **Paper B** of the RECQ/Evento publication
series. Everything needed to replicate the study is here: the specs, the frozen
acceptance test suites, the agent harness, the metrics pipeline, and the raw run records.

## The idea

1. A **framework-neutral specification** (REST contract + behavior) is fixed in `spec/`.
2. A **black-box HTTP acceptance suite** — including classic distributed-systems
   concurrency scenarios (oversell races, duplicate commands, lost updates, saga
   compensation, exactly-once side effects) — is written **first** and frozen in
   `acceptance/`. The tests *are* the requirement (TDD).
3. An **agentic LLM coder** (Claude Code, Codex CLI, Gemini CLI, or an open-weights
   model) is given the spec, the suite, an arm-specific docs pack, and a compiling
   skeleton. It iterates — build, test, fix — until the suite is green or the budget
   is exhausted. The *only* prompt difference between arms is the technology constraint.
4. The harness grades every run identically and records four headline KPIs:

| KPI | What it measures |
|-----|------------------|
| **TIME** | Wall-clock to all-tests-green (or budget exhaustion); time to first green build |
| **BUDGET** | Tokens in/out, $ cost, agent turns |
| **QUALITY** | Rework: code changed *after* the original design due to test failures (per-iteration git history) |
| **PERFORMANCE** | p50/p95/p99 latency, throughput, error rate under a fixed k6 load; JVM + container memory |

Secondary metrics: static quality (cloc, CK, PMD), architecture conformance (ArchUnit
CQRS-discipline rules; Evento's own `strictConfinement` probe), and T2 change impact.

## Replicating

Prerequisites: Docker, Python 3.12 + [uv](https://docs.astral.sh/uv/), JDK 21 + Maven,
and at least one agent CLI (e.g. `claude`).

```sh
cp .env.example .env        # add your API key(s)
uv sync                     # install the harness
./tools/fetch_tools.sh      # download pinned CK / PMD / cloc
make pilot                  # one cell end-to-end (spends API tokens!)
make matrix                 # the full configured matrix (cost gate — read config/matrix.yaml)
make analyze                # runs/ -> results.csv -> stats, plots, LaTeX tables
```

Every run persists its full evidence under `runs/<run_id>/` (generated code with
per-iteration git history, agent transcript, metrics JSON, test reports) — see
`runs/SCHEMA.md`.

All external dependencies are pinned (`config/versions.lock.yaml`): model IDs, Docker
image digests, framework versions, tool checksums.

## Layout

```
config/        experiment matrix, model registry + price tables, arm registry, version pins
spec/          framework-neutral specs: SPEC.md + openapi.yaml + T2_FEATURE.md per domain
acceptance/    the frozen TDD suites (pytest + httpx) — visible to the agent
variant/       anti-gaming re-grade suites (same scenarios, fresh data) — never enter a workspace
perf/          fixed k6 load scripts per domain
docs_packs/    per-arm in-context documentation, token-budget-equalized (see SOURCES.md)
skeletons/     per-arm minimal compiling Maven projects (root package com.study.app)
archunit/      conformance rules run against generated classes
runtime/       per-arm pinned docker-compose runtime dependencies
harness/       Python orchestrator: workspace, prompt, budgets, agents, metrics, persistence
analysis/      aggregation -> Mann-Whitney U + Cliff's delta -> figures + LaTeX tables
runs/          persisted run records (the raw data of the paper)
```

## License

Apache-2.0 (harness and specs). Generated artifacts under `runs/` retain the license
terms of their respective generators/providers; they are published for verification.
