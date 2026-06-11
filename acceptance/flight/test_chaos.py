"""Micro-topology resilience / chaos scenarios.

These are NOT part of normal grading and are NEVER shipped to an agent workspace
(workspace.py skips test_chaos*; this module also skips unless the resilience
driver sets CHAOS_SCENARIO). The driver runs ONE scenario per invocation
(`-k <scenario>`) and injects faults via the workspace's scripts/ helpers
(restart-service.sh / down.sh / up.sh), located through WORKSPACE_DIR.

Service targets come from env vars CRASH_TARGET / DOWNED_DEP (the harness passes
them per the domain registry); we do NOT hardcode service names — fallbacks
('flights' for the contended seat-inventory service, 'payments' for the charge
dependency) are used only when the env is unset.

Invariants asserted are the same conservation/exactly-once/no-double-booking
properties as the concurrency suite — but now they must survive a service crash,
a full restart, and a downed dependency across the network boundary.
"""

from __future__ import annotations

import os
import subprocess
import threading
import time

import pytest

from conftest import (
    LONG_DEADLINE,
    book_seat,
    create_customer,
    create_flight,
    get_json,
    new_booking_id,
    parallel,
    rng,
    seat_ids,
    seats_taken,
    stats,
    wait_all_decided,
    wait_until,
)

SCENARIO = os.environ.get("CHAOS_SCENARIO")
TOPOLOGY = os.environ.get("TOPOLOGY", "single")
WS = os.environ.get("WORKSPACE_DIR", "")

# the contended seat-inventory service and the charge dependency; the harness
# supplies the real names per the domain registry.
CRASH_TARGET = os.environ.get("CRASH_TARGET", "flights")
DOWNED_DEP = os.environ.get("DOWNED_DEP", "payments")

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
    """Kill the seat-inventory service (CRASH_TARGET) during a concurrent
    booking burst, restart it -> no seat double-booked, no seat lost,
    conservation holds at convergence."""
    before = stats(client)
    price = 120
    seat_count = rng.randint(6, 10)
    f = create_flight(client, seat_count=seat_count, seat_price=price)
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=seat_count * price * 3)
    # contend each seat with two bookings so a crash can interleave a race
    bids = [new_booking_id() for _ in range(seat_count + 6)]

    def fire():
        parallel([
            (lambda b=b, i=i: book_seat(client, b, cid, fid, seats[i % seat_count]))
            for i, b in enumerate(bids)
        ])

    t = threading.Thread(target=fire)
    t.start()
    time.sleep(0.3)                       # let some requests land
    _script("restart-service.sh", CRASH_TARGET)   # crash + recover mid-burst
    t.join()

    decided = wait_all_decided(client, bids, deadline=CHAOS_DEADLINE)
    confirmed = [b for b in decided.values() if b["status"] == "CONFIRMED"]
    assert len(confirmed) <= seat_count, \
        f"oversold across a crash: {len(confirmed)} > {seat_count}"
    owned = [b["seat"] for b in confirmed]
    assert len(set(owned)) == len(owned), f"seat double-booked across a crash: {owned}"

    def conserved() -> bool:
        s = stats(client)
        taken = len(seats_taken(client, fid))
        return (taken == len(confirmed)                          # seats sold == seats taken
                and s["confirmed"] - before["confirmed"] == len(confirmed))
    wait_until(conserved, deadline=CHAOS_DEADLINE,
               what="conservation after seat-inventory crash+recovery")


def test_full_restart(client):
    """Place a burst, let it settle, restart ALL services -> committed state
    survives (bookings queryable, seats owned, stats conserved). In-memory
    shortcuts die here; event-sourced arms may rebuild read models by replay."""
    before = stats(client)
    price = 200
    f = create_flight(client, seat_count=20, seat_price=price)
    fid = f["id"]
    seats = seat_ids(f)
    cid = create_customer(client, balance=20 * price)
    bids = [new_booking_id() for _ in range(8)]
    parallel([
        (lambda b=b, s=s: book_seat(client, b, cid, fid, s))
        for b, s in zip(bids, seats)
    ])
    decided = wait_all_decided(client, bids, deadline=LONG_DEADLINE)
    confirmed_before = sum(1 for b in decided.values() if b["status"] == "CONFIRMED")

    _restart_all()                        # cold restart (both topologies)

    def survived() -> bool:
        # every previously-decided booking is still decided & unchanged
        for bid, b in decided.items():
            cur = get_json(client, f"/bookings/{bid}")
            if cur["status"] != b["status"]:
                return False
            if b["status"] == "CONFIRMED" and cur["seat"] != b["seat"]:
                return False
        s = stats(client)
        return s["confirmed"] - before["confirmed"] == confirmed_before
    wait_until(survived, deadline=CHAOS_DEADLINE,
               what="state durability across a full restart")


@micro_only
def test_saga_under_downed_dep(client):
    """The charge dependency (DOWNED_DEP) is DOWN when a held seat must be
    charged. On recovery the booking resolves EXACTLY once — confirm or
    compensate+release seat — never a double charge, never a leaked seat."""
    price = 500
    f = create_flight(client, seat_count=5, seat_price=price)
    fid = f["id"]
    seat = seat_ids(f)[0]
    cid = create_customer(client, balance=price * 3)
    start_balance = get_json(client, f"/customers/{cid}")["balance"]

    _script("restart-service.sh", DOWNED_DEP, "stop")    # take the charge dep down
    bid = new_booking_id()
    book_seat(client, bid, cid, fid, seat)
    time.sleep(2.0)                       # booking is stuck mid-saga (cannot charge)
    _script("restart-service.sh", DOWNED_DEP)            # bring it back

    b = {}
    def decided() -> bool:
        cur = client.get(f"/bookings/{bid}")
        if cur.status_code == 200 and cur.json()["status"] in ("CONFIRMED", "REJECTED"):
            b.update(cur.json())
            return True
        return False
    wait_until(decided, deadline=CHAOS_DEADLINE, what="booking resolves after dep recovery")

    def consistent() -> bool:
        balance = get_json(client, f"/customers/{cid}")["balance"]
        taken = len(seats_taken(client, fid))
        if b["status"] == "CONFIRMED":
            return balance == start_balance - price and taken == 1
        return balance == start_balance and taken == 0       # clean compensation, seat freed
    wait_until(consistent, deadline=CHAOS_DEADLINE,
               what="exactly-once resolution across a downed dependency")
