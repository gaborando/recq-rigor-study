"""Workspace provisioning + per-run runtime infra (docker compose project).

Pilot mode runs the agent on the host in an isolated directory; runtime
dependencies (Postgres / Evento server) are containerized per run with
ephemeral host ports, so parallel/sequential runs never collide.
"""

from __future__ import annotations

import json
import shutil
import socket
import subprocess
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from .config import REPO, RUNS, CellSpec, load

GIT_ID = ["-c", "user.email=harness@recq-rigor-study", "-c", "user.name=harness"]

# Auto-commit the workspace after every file-modifying agent tool call (the
# commit history IS the QUALITY/rework evidence) and log every Bash tool call
# with a wall-clock timestamp (TIME correlation: first/last test run, builds).
_BASH_LOGGER = (
    "python3 -c \"import sys,json,time; d=json.load(sys.stdin); "
    "open('.harness-events.jsonl','a').write(json.dumps({'ts': time.time(), "
    "'cmd': (d.get('tool_input') or {}).get('command','')})+chr(10))\" || true"
)

CLAUDE_SETTINGS = {
    "hooks": {
        "PostToolUse": [
            {
                "matcher": "Edit|Write|MultiEdit|NotebookEdit",
                "hooks": [{
                    "type": "command",
                    "command": "git add -A . && git -c user.email=agent@run -c user.name=agent "
                               "commit -q -m \"iter $(date -u +%Y-%m-%dT%H:%M:%SZ)\" || true",
                }],
            },
            {
                "matcher": "Bash",
                "hooks": [{"type": "command", "command": _BASH_LOGGER}],
            },
        ]
    }
}


def sh(cmd: list[str] | str, cwd: Path | None = None, check: bool = True,
       timeout: int | None = None, env: dict | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, cwd=cwd, check=check, capture_output=True, text=True,
                          timeout=timeout, env=env, shell=isinstance(cmd, str))


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@dataclass
class Workspace:
    run_id: str
    cell: CellSpec
    root: Path                 # runs/<run_id>/
    dir: Path                  # runs/<run_id>/workspace/
    compose_file: Path
    app_port: int = 0
    env: dict[str, str] = field(default_factory=dict)   # .run-env content

    @property
    def compose_project(self) -> str:
        # compose project names: lowercase alphanumerics + -_
        return "run-" + self.run_id.replace("__", "-").replace("_", "-").lower()[:50]

    def compose(self, *args: str, check: bool = True) -> subprocess.CompletedProcess:
        return sh(["docker", "compose", "-p", self.compose_project,
                   "-f", str(self.compose_file), *args], check=check, timeout=600)

    def service_port(self, service: str, container_port: int) -> int:
        out = self.compose("port", service, str(container_port)).stdout.strip()
        return int(out.rsplit(":", 1)[1])


def make_run_id(cell: CellSpec) -> str:
    ts = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    return f"{cell.domain}__{cell.arm}__{cell.model}__{cell.task}__rep{cell.rep}__{ts}"


def _resolve_t1_workspace(cell: CellSpec) -> Path:
    """For T2: find the same cell's most recent T1 final workspace."""
    prefix = f"{cell.domain}__{cell.arm}__{cell.model}__t1__rep{cell.rep}__"
    candidates = sorted(d for d in RUNS.iterdir() if d.name.startswith(prefix))
    if not candidates:
        raise FileNotFoundError(f"no T1 run found for cell prefix {prefix}")
    return candidates[-1] / "workspace"


def provision(cell: CellSpec) -> Workspace:
    arms = load("arms")["arms"]
    arm = arms[cell.arm]
    run_id = make_run_id(cell)
    root = RUNS / run_id
    ws = root / "workspace"
    (root / "agent").mkdir(parents=True)
    (root / "metrics").mkdir()
    (root / "reports").mkdir()
    (root / "logs").mkdir()

    # 1) project tree: skeleton (T1) or the same cell's T1 result (T2)
    if cell.task == "t1":
        shutil.copytree(REPO / arm["skeleton"], ws)
    else:
        src = _resolve_t1_workspace(cell)
        shutil.copytree(src, ws, ignore=shutil.ignore_patterns(
            ".git", "target", "app.log", ".app.pid", "nohup.out"))

    # 2) spec + docs + acceptance suite (the TDD contract)
    spec_dir = REPO / "spec" / cell.domain
    shutil.copy(spec_dir / "SPEC.md", ws / "SPEC.md")
    shutil.copy(spec_dir / "openapi.yaml", ws / "openapi.yaml")
    if cell.task == "t2":
        shutil.copy(spec_dir / "T2_FEATURE.md", ws / "T2_FEATURE.md")

    acc_src = REPO / "acceptance" / cell.domain
    acc_dst = ws / "acceptance-tests"
    acc_dst.mkdir(exist_ok=True)
    for f in acc_src.glob("*.py"):
        if cell.task == "t1" and f.name.startswith("test_t2"):
            continue  # T2 material must not leak into T1 workspaces
        shutil.copy(f, acc_dst / f.name)

    docs_src = REPO / arm["docs_pack"]
    if docs_src.exists():
        shutil.copytree(docs_src, ws / "docs", dirs_exist_ok=True)

    # 3) per-iteration auto-commit hook for the agent CLI
    claude_dir = ws / ".claude"
    claude_dir.mkdir(exist_ok=True)
    (claude_dir / "settings.json").write_text(json.dumps(CLAUDE_SETTINGS, indent=2))

    # 4) git baseline
    sh(["git", "init", "-q", "-b", "main"], cwd=ws)
    sh(["git", "add", "-A", "."], cwd=ws)
    sh(["git", *GIT_ID, "commit", "-q", "-m", "baseline: skeleton + spec + acceptance suite"], cwd=ws)

    w = Workspace(run_id=run_id, cell=cell, root=root, dir=ws,
                  compose_file=REPO / arm["runtime_compose"])
    return w


def start_infra(w: Workspace) -> None:
    """Bring up the per-run compose project and write .run-env."""
    w.compose("up", "-d", "--wait")
    w.app_port = free_port()
    env = {"PORT": str(w.app_port), "TASK": w.cell.task}

    if w.cell.arm == "arm_a_evento":
        env["EVENTO_HOST"] = "localhost"
        env["EVENTO_PORT"] = str(w.service_port("evento-server", 3030))
        env["EVENTO_HTTP_PORT"] = str(w.service_port("evento-server", 3000))
        env["DB_URL"] = f"jdbc:postgresql://localhost:{w.service_port('appdb', 5432)}/app"
    else:
        env["DB_URL"] = f"jdbc:postgresql://localhost:{w.service_port('database', 5432)}/app"
    env["DB_USER"] = "postgres"
    env["DB_PASS"] = "secret"

    w.env = env
    (w.dir / ".run-env").write_text("".join(f"{k}={v}\n" for k, v in env.items()))


def reset_infra(w: Workspace) -> None:
    """Fresh state for grading: recreate containers/volumes, keep ports stable
    by rewriting .run-env afterwards."""
    w.compose("down", "-v")
    start_infra(w)


def teardown(w: Workspace) -> None:
    w.compose("down", "-v", check=False)
    # stop a leftover app process if any
    pid = w.dir / ".app.pid"
    if pid.exists():
        sh(["bash", "scripts/app.sh", "stop"], cwd=w.dir, check=False)
