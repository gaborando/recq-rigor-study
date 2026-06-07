"""Final build & boot for grading (independent of whatever the agent did)."""

from __future__ import annotations

import subprocess
import time
from pathlib import Path

import httpx

from .workspace import Workspace, sh


def final_build(w: Workspace, log: Path) -> bool:
    r = sh(["mvn", "-B", "-DskipTests", "package"], cwd=w.dir, check=False, timeout=900)
    log.write_text(r.stdout + "\n--- stderr ---\n" + r.stderr)
    return r.returncode == 0


def _read_pids(w: Workspace) -> list[int]:
    """Collect app PIDs: .app.pid (single) or .app-pids (micro, one per line)."""
    pids: list[int] = []
    for name in (".app.pid", ".app-pids"):
        f = w.dir / name
        if f.exists():
            pids += [int(x) for x in f.read_text().split() if x.strip().isdigit()]
    return pids


def boot(w: Workspace, log: Path, attempts: int = 3) -> tuple[bool, bool, list[int]]:
    """Returns (boots_cleanly, boot_flaky, app_pids).

    Single topology: scripts/app.sh restart. Micro: scripts/up.sh starts all
    services and waits each /actuator/health (edge is the suite's app_port)."""
    script, action = ("scripts/up.sh", []) if w.cell.topology == "micro" \
        else ("scripts/app.sh", ["restart"])
    flaky = False
    for attempt in range(1, attempts + 1):
        r = sh(["bash", script, *action], cwd=w.dir, check=False, timeout=600)
        with open(log, "a") as f:
            f.write(f"--- boot attempt {attempt} ({script}) ---\n{r.stdout}\n{r.stderr}\n")
        if r.returncode == 0:
            return True, flaky, _read_pids(w)
        flaky = True
        time.sleep(3)
    return False, flaky, []


def stop(w: Workspace) -> None:
    if w.cell.topology == "micro":
        sh(["bash", "scripts/down.sh"], cwd=w.dir, check=False, timeout=120)
    else:
        sh(["bash", "scripts/app.sh", "stop"], cwd=w.dir, check=False, timeout=60)


def wait_healthy(port: int, timeout_s: int = 60) -> bool:
    end = time.monotonic() + timeout_s
    while time.monotonic() < end:
        try:
            r = httpx.get(f"http://localhost:{port}/actuator/health", timeout=3)
            if r.status_code == 200 and r.json().get("status") == "UP":
                return True
        except httpx.HTTPError:
            pass
        time.sleep(1)
    return False


def strict_confinement_probe(w: Workspace, log: Path) -> bool | None:
    """Arm A only: would the app fail to start with strictConfinement=true?
    Returns True if start-up FAILS under strict mode (violations exist)."""
    if w.cell.arm != "arm_a_evento" or w.cell.topology != "single":
        return None
    stop(w)
    env_file = w.dir / ".run-env"
    original = env_file.read_text()
    try:
        env_file.write_text(original + "EVENTO_STRICT=true\n")
        r = sh(["bash", "scripts/app.sh", "restart"], cwd=w.dir, check=False, timeout=300)
        with open(log, "a") as f:
            f.write(f"--- strictConfinement probe (exit {r.returncode}) ---\n{r.stdout}\n{r.stderr}\n")
        return r.returncode != 0
    finally:
        env_file.write_text(original)
        stop(w)
