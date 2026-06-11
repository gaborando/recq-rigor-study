"""Shared fixtures/helpers for the flight seat-reservation acceptance suite.

Black-box HTTP only. Reads may be eventually consistent: ALWAYS assert through
the polling helpers, never via sleep-and-hope.

Environment:
  BASE_URL          target system            (default http://localhost:8080)
  DEADLINE_SECONDS  convergence deadline     (default 10, per SPEC.md)
  RNG_SEED          optional reproducibility seed; unset = random per execution
"""

from __future__ import annotations

import os
import random
import string
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from typing import Any, Callable

import httpx
import pytest

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080").rstrip("/")
DEADLINE = float(os.environ.get("DEADLINE_SECONDS", "10"))
LONG_DEADLINE = 3 * DEADLINE  # for burst convergence (many async decisions)

_seed = os.environ.get("RNG_SEED")
rng = random.Random(int(_seed)) if _seed else random.Random()


@pytest.fixture(scope="session")
def client() -> httpx.Client:
    with httpx.Client(base_url=BASE_URL, timeout=30.0) as c:
        yield c


# ---------------------------------------------------------------- helpers

def rand_name(prefix: str) -> str:
    return f"{prefix}-" + "".join(rng.choices(string.ascii_lowercase + string.digits, k=10))


def new_booking_id() -> str:
    return str(uuid.UUID(int=rng.getrandbits(128), version=4))


def wait_until(probe: Callable[[], bool], deadline: float = DEADLINE, what: str = "condition") -> None:
    """Poll `probe` until truthy or the deadline elapses."""
    end = time.monotonic() + deadline
    last_err: Exception | None = None
    while time.monotonic() < end:
        try:
            if probe():
                return
            last_err = None
        except (httpx.HTTPError, AssertionError, KeyError) as e:  # transient view lag
            last_err = e
        time.sleep(0.2)
    pytest.fail(f"timed out after {deadline}s waiting for {what}"
                + (f" (last error: {last_err})" if last_err else ""))


def get_json(client: httpx.Client, path: str) -> Any:
    r = client.get(path)
    assert r.status_code == 200, f"GET {path} -> {r.status_code}: {r.text[:200]}"
    return r.json()


def parallel(callables: list[Callable[[], Any]], max_workers: int | None = None) -> list[Any]:
    """Fire callables concurrently; return results in submission order."""
    with ThreadPoolExecutor(max_workers=max_workers or len(callables)) as pool:
        return [f.result() for f in [pool.submit(c) for c in callables]]


# ---------------------------------------------------------------- factories

def create_flight(client: httpx.Client, *, seat_count: int, seat_price: int) -> dict:
    r = client.post("/flights", json={"seatCount": seat_count, "seatPrice": seat_price})
    assert r.status_code == 201, f"POST /flights -> {r.status_code}: {r.text[:200]}"
    return r.json()


def create_customer(client: httpx.Client, *, balance: int) -> str:
    r = client.post("/customers", json={"name": rand_name("cust"), "balance": balance})
    assert r.status_code == 201, f"POST /customers -> {r.status_code}: {r.text[:200]}"
    return str(r.json()["id"])


def seat_ids(flight: dict) -> list[str]:
    """The seat labels offered by a flight, as reported by the create/view payload."""
    return [s["seat"] for s in flight["seats"]]


def flight_view(client: httpx.Client, flight_id: str) -> dict:
    return get_json(client, f"/flights/{flight_id}")


def seats_taken(client: httpx.Client, flight_id: str) -> list[dict]:
    """Seats currently unavailable (held or booked)."""
    return [s for s in flight_view(client, flight_id)["seats"] if not s["available"]]


def seat_state(client: httpx.Client, flight_id: str, seat: str) -> dict:
    for s in flight_view(client, flight_id)["seats"]:
        if s["seat"] == seat:
            return s
    raise KeyError(f"seat {seat} not on flight {flight_id}")


def book_seat(client: httpx.Client, booking_id: str, customer_id: str, flight_id: str,
              seat: str) -> httpx.Response:
    return client.post("/bookings", json={
        "bookingId": booking_id, "customerId": customer_id,
        "flightId": flight_id, "seat": seat,
    })


def booking_status(client: httpx.Client, booking_id: str) -> dict | None:
    r = client.get(f"/bookings/{booking_id}")
    if r.status_code == 404:  # allowed only before the write converges
        return None
    assert r.status_code == 200
    return r.json()


def wait_decided(client: httpx.Client, booking_id: str, deadline: float = DEADLINE) -> dict:
    """Poll until the booking leaves PENDING; return the decided view."""
    result: dict = {}

    def probe() -> bool:
        b = booking_status(client, booking_id)
        if b and b["status"] in ("CONFIRMED", "REJECTED"):
            result.update(b)
            return True
        return False

    wait_until(probe, deadline, what=f"booking {booking_id} decision")
    return result


def wait_all_decided(client: httpx.Client, booking_ids: list[str],
                     deadline: float = LONG_DEADLINE) -> dict[str, dict]:
    """Wait for a burst of bookings to all reach a decision."""
    decided: dict[str, dict] = {}

    def probe() -> bool:
        for bid in booking_ids:
            if bid in decided:
                continue
            b = booking_status(client, bid)
            if b and b["status"] in ("CONFIRMED", "REJECTED"):
                decided[bid] = b
        return len(decided) == len(booking_ids)

    wait_until(probe, deadline, what=f"{len(booking_ids)} booking decisions")
    return decided


def notifications_for(client: httpx.Client, customer_id: str) -> list[dict]:
    return get_json(client, f"/customers/{customer_id}/notifications")


def stats(client: httpx.Client) -> dict:
    return get_json(client, "/stats/bookings")
