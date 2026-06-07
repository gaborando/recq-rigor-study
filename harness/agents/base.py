"""Uniform agent adapter interface."""

from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from pathlib import Path

from ..config import BudgetCfg


@dataclass
class AgentResult:
    exit_code: int
    wall_seconds: float
    killed_reason: str | None = None          # wall|tokens|None
    # usage as reported by the CLI (None where unavailable)
    tokens_in: int | None = None
    tokens_out: int | None = None
    cache_tokens: int | None = None
    turns: int | None = None
    tool_invocations: int | None = None
    cost_usd: float | None = None
    cost_source: str | None = None            # reported|computed|estimated
    active_agent_seconds: float | None = None
    cli_version: str = "unknown"
    extra: dict = field(default_factory=dict)


class AgentAdapter(ABC):
    name: str

    @abstractmethod
    def run(self, workspace: Path, prompt: str, budget: BudgetCfg,
            model_id: str, transcript_path: Path, stdout_path: Path,
            env: dict[str, str]) -> AgentResult: ...
