"""Final build & boot for grading (independent of whatever the agent did)."""

from __future__ import annotations

import os
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
    """Returns (boots_cleanly, boot_flaky, app_pids). scripts/up.sh starts every
    service and waits each /actuator/health (edge is the suite's app_port)."""
    flaky = False
    for attempt in range(1, attempts + 1):
        # grading boot: require full bundle-readiness (health UP is too early for
        # broker bundles), so the suite never hits a not-yet-registered bundle.
        r = sh(["bash", "scripts/up.sh"], cwd=w.dir, check=False, timeout=600,
               env={**os.environ, "READY_REQUIRE_BUNDLE": "1"})
        with open(log, "a") as f:
            f.write(f"--- boot attempt {attempt} ---\n{r.stdout}\n{r.stderr}\n")
        if r.returncode == 0:
            return True, flaky, _read_pids(w)
        flaky = True
        time.sleep(3)
    return False, flaky, []


def stop(w: Workspace) -> None:
    sh(["bash", "scripts/down.sh"], cwd=w.dir, check=False, timeout=120)


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
