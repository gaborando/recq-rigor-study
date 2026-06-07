"""Deterministic prompt assembly. The arm constraint clause is the ONLY
prose that differs between arms; everything else is identical per (domain, task)."""

from __future__ import annotations

from .config import CellSpec, load

T1_TEMPLATE = """\
You are a senior backend engineer. Implement the system specified in ./SPEC.md
(REST contract: ./openapi.yaml) inside this project.

TECHNOLOGY CONSTRAINT:
{constraint}

TEST-DRIVEN DEFINITION OF DONE:
The acceptance test suite in ./acceptance-tests is the requirement — including
its concurrency scenarios, which are part of the contract. You are done when
`make test` passes completely (it builds the app, restarts it, and runs the
whole suite against it). Iterate: implement, run `make test`, fix, repeat.

GROUND RULES:
- Work only inside this directory. Runtime services (database{evento}) are
  already running; their addresses are in .run-env and are wired into the
  skeleton's configuration. Never edit .run-env.
- Keep the delivery requirements of SPEC.md: root package com.study.app with
  the prescribed layer sub-packages; do not change the skeleton's build
  coordinates, dependency constraints, or pre-wired configuration.
- Reference documentation for your stack is in ./docs.
- Do not modify anything under ./acceptance-tests — the tests are the contract.
- `make build` compiles; `make run` (re)starts the app; `make test` is the
  full check. The app log is ./app.log.

When the suite is fully green, stop and summarize what you built.
"""

T2_TEMPLATE = """\
You are a senior backend engineer returning to a system you previously built
(it is in this directory, already passing its original acceptance suite).

Evolve it: implement the feature specified in ./T2_FEATURE.md. The system
specification (./SPEC.md, ./openapi.yaml) still applies.

TECHNOLOGY CONSTRAINT (unchanged):
{constraint}

TEST-DRIVEN DEFINITION OF DONE:
The extended acceptance suite in ./acceptance-tests (now including the
cancellation scenarios) is the requirement. You are done when `make test`
passes completely. Iterate: implement, run `make test`, fix, repeat.

GROUND RULES:
- Work only inside this directory. Runtime services are already running;
  addresses are in .run-env (never edit it).
- Keep the delivery requirements of SPEC.md (root package, layers, skeleton
  constraints). Reference documentation is in ./docs.
- Do not modify anything under ./acceptance-tests — the tests are the contract.

When the suite is fully green, stop and summarize what you changed.
"""


def build_prompt(cell: CellSpec) -> str:
    arm = load("arms")["arms"][cell.arm]
    constraint = arm["constraint_clause"].strip()
    evento = ", Evento server" if cell.arm == "arm_a_evento" else ""
    tpl = T1_TEMPLATE if cell.task == "t1" else T2_TEMPLATE
    return tpl.format(constraint=constraint, evento=evento)
