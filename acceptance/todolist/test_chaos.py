"""Micro-topology resilience / chaos scenarios.

These are NOT part of normal grading and are NEVER shipped to an agent workspace
(workspace.py skips test_chaos*; this module also skips unless the resilience
driver sets CHAOS_SCENARIO). The driver runs ONE scenario per invocation
(`-k <scenario>`) and injects faults via the workspace's scripts/ helpers
(restart-service.sh / down.sh / up.sh), located through WORKSPACE_DIR.

Invariants asserted are the same exactly-once-completion / conservation
properties as the concurrency suite — but now they must survive a service
crash, a full restart, and a downed dependency across the network boundary.

Service targets come from the env (the harness passes them): CRASH_TARGET
(default 'items') is the service crashed mid-burst; DOWNED_DEP (default
'notifications') is the dependency taken down across a completion.
"""

from __future__ import annotations

import os
import subprocess
import threading
import time

import pytest

from conftest import (
    LONG_DEADLINE,
    add_item,
    check_item,
    completion_count,
    create_list,
    items_of,
    list_status,
    list_view,
    make_list_with_items,
    parallel,
    rng,
    stats,
    wait_until,
)

SCENARIO = os.environ.get("CHAOS_SCENARIO")
WS = os.environ.get("WORKSPACE_DIR", "")
CRASH_TARGET = os.environ.get("CRASH_TARGET", "items")
DOWNED_DEP = os.environ.get("DOWNED_DEP", "notifications")

# chaos runs only under the resilience driver (CHAOS_SCENARIO set).
pytestmark = pytest.mark.skipif(
    not SCENARIO,
    reason="chaos scenarios run only under the resilience driver",
)

CHAOS_DEADLINE = 4 * LONG_DEADLINE  # restarts + replay need generous convergence


def _script(name: str, *args: str) -> None:
    """Run a workspace lifecycle script (kill/restart a service, etc.)."""
    r = subprocess.run(["bash", f"scripts/{name}", *args], cwd=WS,
                       capture_output=True, text=True, timeout=600)
    if r.returncode != 0:
        raise RuntimeError(f"scripts/{name} {args} failed: {r.stderr[-300:]}")


def _restart_all() -> None:
    """Cold-restart every service (the database containers stay up), so
    durably-persisted state must survive."""
    _script("down.sh")
    _script("up.sh")


def test_crash_mid_burst(client):
    """Kill the items service during a concurrent check-burst, restart it →
    no check lost, completion still EXACTLY-ONCE, conservation holds."""
    before = stats(client)
    n = rng.randint(8, 12)
    lid, iids = make_list_with_items(client, n)  # n unchecked items

    def fire():
        parallel([(lambda i=i: check_item(client, lid, i)) for i in iids])

    t = threading.Thread(target=fire)
    t.start()
    time.sleep(0.3)                              # let some checks land
    _script("restart-service.sh", CRASH_TARGET)  # crash + recover mid-burst
    t.join()

    # every check must eventually take effect -> the list completes exactly once
    wait_until(lambda: list_status(client, lid) == "COMPLETED",
               deadline=CHAOS_DEADLINE, what="list completes across a crash")

    def conserved() -> bool:
        items = items_of(client, lid)
        if len(items) != n or not all(it["checked"] for it in items):
            return False
        c = completion_count(client, lid)
        assert c <= 1, f"duplicate completion across a crash: {c}"
        s = stats(client)
        return (c == 1
                and s["completed"] - before["completed"] == 1
                and s["checkedItems"] - before["checkedItems"] == n)

    wait_until(conserved, deadline=CHAOS_DEADLINE,
               what=f"exactly-once completion + conservation after {CRASH_TARGET} crash")


def test_full_restart(client):
    """Complete some lists, let them settle, restart ALL services → committed
    lists/items/completion state survives (queryable, stats conserved).
    In-memory shortcuts die here; event-sourced arms may rebuild by replay."""
    before = stats(client)
    b = 6
    completed: list[str] = []
    active_partial: list[tuple[str, list[str]]] = []
    for _ in range(b):
        lid, iids = make_list_with_items(client, 2)
        for iid in iids:
            check_item(client, lid, iid)
        wait_until(lambda l=lid: list_status(client, l) == "COMPLETED",
                   deadline=LONG_DEADLINE, what="list completed before restart")
        completed.append(lid)
    # one list left ACTIVE (partially checked) to verify non-completed survival too
    plid, piids = make_list_with_items(client, 2)
    check_item(client, plid, piids[0])
    active_partial.append((plid, piids))

    _restart_all()                               # cold restart (both topologies)

    def survived() -> bool:
        for lid in completed:
            if list_status(client, lid) != "COMPLETED":
                return False
            c = completion_count(client, lid)
            assert c <= 1, f"duplicate completion after restart on {lid}: {c}"
            if c != 1:
                return False
        for lid, _ in active_partial:
            if list_status(client, lid) != "ACTIVE":
                return False
        s = stats(client)
        return s["completed"] - before["completed"] == b

    wait_until(survived, deadline=CHAOS_DEADLINE,
               what="completion state durable across a full restart")


def test_saga_under_downed_dep(client):
    """The notifications service is DOWN when a list completes. On recovery the
    completion notification is delivered EXACTLY once — no duplicate, no loss."""
    lid, iids = make_list_with_items(client, 2)

    _script("restart-service.sh", DOWNED_DEP, "stop")   # take the dependency down
    for iid in iids:
        check_item(client, lid, iid)                    # completes while dep is down
    # the list-completion decision itself must still land (lists service owns it)
    wait_until(lambda: list_status(client, lid) == "COMPLETED",
               deadline=CHAOS_DEADLINE, what="completion decided while dep down")
    time.sleep(2.0)                                     # notification is stuck/queued
    _script("restart-service.sh", DOWNED_DEP)           # bring it back

    def delivered_exactly_once() -> bool:
        c = completion_count(client, lid)
        assert c <= 1, f"duplicate completion notification after recovery: {c}"
        return c == 1

    wait_until(delivered_exactly_once, deadline=CHAOS_DEADLINE,
               what="exactly-once completion notification across a downed dependency")
