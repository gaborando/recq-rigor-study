"""Micro-topology resilience / chaos scenarios.

These are NOT part of normal grading and are NEVER shipped to an agent workspace
(workspace.py skips test_chaos*; this module also skips unless the resilience
driver sets CHAOS_SCENARIO). The driver runs ONE scenario per invocation
(`-k <scenario>`) and injects faults via the workspace's scripts/ helpers
(restart-service.sh / down.sh / up.sh), located through WORKSPACE_DIR.

Invariants asserted are the same conservation/exactly-once properties as the
concurrency suite — but now they must survive a service crash, a full restart,
and a downed dependency across the network boundary.
"""

from __future__ import annotations

import os
import subprocess
import threading
import time

import pytest

from conftest import (
    LONG_DEADLINE,
    create_customer,
    create_product,
    get_json,
    new_order_id,
    parallel,
    place_order,
    rng,
    stats,
    wait_all_decided,
    wait_until,
)

SCENARIO = os.environ.get("CHAOS_SCENARIO")
TOPOLOGY = os.environ.get("TOPOLOGY", "single")
WS = os.environ.get("WORKSPACE_DIR", "")
# which stateful services the chaos scenarios hit (from the domain registry)
CRASH_TARGET = os.environ.get("CRASH_TARGET", "inventory")
DOWNED_DEP = os.environ.get("DOWNED_DEP", "customers")

# chaos runs only under the resilience driver (CHAOS_SCENARIO set). full_restart
# applies to BOTH topologies; the per-service scenarios are micro-only (guarded
# per-test below).
pytestmark = pytest.mark.skipif(
    not SCENARIO,
    reason="chaos scenarios run only under the resilience driver",
)
micro_only = pytest.mark.skipif(TOPOLOGY != "micro", reason="micro-topology scenario")

CHAOS_DEADLINE = 4 * LONG_DEADLINE  # restarts + replay need generous convergence


def _script(name: str, *args: str) -> None:
    """Run a workspace lifecycle script (kill/restart a service, etc.)."""
    r = subprocess.run(["bash", f"scripts/{name}", *args], cwd=WS,
                       capture_output=True, text=True, timeout=600)
    if r.returncode != 0:
        raise RuntimeError(f"scripts/{name} {args} failed: {r.stderr[-300:]}")


def _restart_all() -> None:
    """Cold-restart the whole system: micro restarts every service; single
    restarts the one app. The database container stays up either way, so
    durably-persisted state must survive."""
    if TOPOLOGY == "micro":
        _script("down.sh")
        _script("up.sh")
    else:
        _script("app.sh", "restart")


@micro_only
def test_crash_mid_burst(client):
    """Kill `inventory` during a concurrent order burst, restart it →
    no order lost, stock never corrupted, conservation holds at convergence."""
    before = stats(client)
    price = 120
    stock = rng.randint(6, 10)
    pid = create_product(client, unit_price=price, stock=stock)
    cid = create_customer(client, balance=stock * price * 3)
    oids = [new_order_id() for _ in range(stock + 6)]

    def fire():
        parallel([(lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids])

    t = threading.Thread(target=fire)
    t.start()
    time.sleep(0.3)                       # let some requests land
    _script("restart-service.sh", CRASH_TARGET)   # crash + recover mid-burst
    t.join()

    decided = wait_all_decided(client, oids, deadline=CHAOS_DEADLINE)
    confirmed = sum(1 for o in decided.values() if o["status"] == "CONFIRMED")
    assert confirmed <= stock, f"oversold across a crash: {confirmed} > {stock}"

    def conserved() -> bool:
        s = stats(client)
        remaining = get_json(client, f"/products/{pid}")["stock"]
        return (remaining == stock - confirmed
                and s["confirmed"] - before["confirmed"] == confirmed)
    wait_until(conserved, deadline=CHAOS_DEADLINE,
               what="conservation after inventory crash+recovery")


def test_full_restart(client):
    """Place a burst, let it settle, restart ALL services → committed state
    survives (orders queryable, stats conserved). In-memory shortcuts die here;
    event-sourced arms may rebuild read models by replay."""
    before = stats(client)
    price = 200
    pid = create_product(client, unit_price=price, stock=20)
    cid = create_customer(client, balance=20 * price)
    oids = [new_order_id() for _ in range(8)]
    parallel([(lambda o=o: place_order(client, o, cid, pid, quantity=1)) for o in oids])
    decided = wait_all_decided(client, oids, deadline=LONG_DEADLINE)
    confirmed_before = sum(1 for o in decided.values() if o["status"] == "CONFIRMED")

    _restart_all()                        # cold restart (both topologies)

    def survived() -> bool:
        # every previously-decided order is still decided & unchanged
        for oid, o in decided.items():
            cur = get_json(client, f"/orders/{oid}")
            if cur["status"] != o["status"]:
                return False
        s = stats(client)
        return s["confirmed"] - before["confirmed"] == confirmed_before
    wait_until(survived, deadline=CHAOS_DEADLINE,
               what="state durability across a full restart")


@micro_only
def test_saga_under_downed_dep(client):
    """`customers` (the charge step) is DOWN when a reservation needs charging.
    On recovery the order resolves EXACTLY once — confirm or compensate — never
    a double charge, never a leaked reservation."""
    price = 500
    pid = create_product(client, unit_price=price, stock=5)
    cid = create_customer(client, balance=price * 3)
    start_stock = get_json(client, f"/products/{pid}")["stock"]
    start_balance = get_json(client, f"/customers/{cid}")["balance"]

    _script("restart-service.sh", DOWNED_DEP, "stop")    # take the dependency down
    oid = new_order_id()
    place_order(client, oid, cid, pid, quantity=1)
    time.sleep(2.0)                       # order is stuck mid-saga (cannot charge)
    _script("restart-service.sh", DOWNED_DEP)            # bring it back

    o = {}
    def decided() -> bool:
        cur = client.get(f"/orders/{oid}")
        if cur.status_code == 200 and cur.json()["status"] in ("CONFIRMED", "REJECTED"):
            o.update(cur.json())
            return True
        return False
    wait_until(decided, deadline=CHAOS_DEADLINE, what="order resolves after dep recovery")

    def consistent() -> bool:
        stock = get_json(client, f"/products/{pid}")["stock"]
        balance = get_json(client, f"/customers/{cid}")["balance"]
        if o["status"] == "CONFIRMED":
            return stock == start_stock - 1 and balance == start_balance - price
        return stock == start_stock and balance == start_balance   # clean compensation
    wait_until(consistent, deadline=CHAOS_DEADLINE,
               what="exactly-once resolution across a downed dependency")
