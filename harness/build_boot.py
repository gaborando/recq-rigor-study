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


def boot(w: Workspace, log: Path, attempts: int = 3) -> tuple[bool, bool, int | None]:
    """Returns (boots_cleanly, boot_flaky, app_pid)."""
    flaky = False
    for attempt in range(1, attempts + 1):
        r = sh(["bash", "scripts/app.sh", "restart"], cwd=w.dir, check=False, timeout=300)
        with open(log, "a") as f:
            f.write(f"--- boot attempt {attempt} ---\n{r.stdout}\n{r.stderr}\n")
        if r.returncode == 0:
            pid_file = w.dir / ".app.pid"
            pid = int(pid_file.read_text().strip()) if pid_file.exists() else None
            return True, flaky, pid
        flaky = True
        time.sleep(3)
    return False, flaky, None


def stop(w: Workspace) -> None:
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
    if w.cell.arm != "arm_a_evento":
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
