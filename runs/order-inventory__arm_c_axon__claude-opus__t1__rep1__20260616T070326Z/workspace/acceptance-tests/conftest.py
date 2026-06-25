"""Shared fixtures/helpers for the order-inventory acceptance suite.

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


def new_order_id() -> str:
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


def read_settled(client: httpx.Client, path: str, deadline: float = DEADLINE) -> Any:
    """GET that tolerates read-after-write lag: polls until the read model has
    materialised (HTTP 200) or the deadline elapses. The read side is eventually
    consistent — reading back a just-written entity may 404 briefly, which is a
    design property, not a fault. Use this for reading freshly-written entities;
    business invariants (conservation, exactly-once) are still asserted strictly
    via wait_until elsewhere."""
    end = time.monotonic() + deadline
    last: httpx.Response | None = None
    while time.monotonic() < end:
        last = client.get(path)
        if last.status_code == 200:
            return last.json()
        time.sleep(0.1)
    assert False, (f"GET {path} not readable within {deadline}s "
                   f"(last {last.status_code if last is not None else 'n/a'})")


def parallel(callables: list[Callable[[], Any]], max_workers: int | None = None) -> list[Any]:
    """Fire callables concurrently; return results in submission order."""
    with ThreadPoolExecutor(max_workers=max_workers or len(callables)) as pool:
        return [f.result() for f in [pool.submit(c) for c in callables]]


# ---------------------------------------------------------------- factories

def create_product(client: httpx.Client, *, unit_price: int, stock: int) -> str:
    r = client.post("/products", json={"name": rand_name("prod"), "unitPrice": unit_price, "stock": stock})
    assert r.status_code == 201, f"POST /products -> {r.status_code}: {r.text[:200]}"
    return str(r.json()["id"])


def create_customer(client: httpx.Client, *, balance: int) -> str:
    r = client.post("/customers", json={"name": rand_name("cust"), "balance": balance})
    assert r.status_code == 201, f"POST /customers -> {r.status_code}: {r.text[:200]}"
    return str(r.json()["id"])


def place_order(client: httpx.Client, order_id: str, customer_id: str, product_id: str,
                quantity: int) -> httpx.Response:
    return client.post("/orders", json={
        "orderId": order_id, "customerId": customer_id,
        "productId": product_id, "quantity": quantity,
    })


def order_status(client: httpx.Client, order_id: str) -> dict | None:
    r = client.get(f"/orders/{order_id}")
    if r.status_code == 404:  # allowed only before the write converges
        return None
    assert r.status_code == 200
    return r.json()


def wait_decided(client: httpx.Client, order_id: str, deadline: float = DEADLINE) -> dict:
    """Poll until the order leaves PENDING; return the decided view."""
    result: dict = {}

    def probe() -> bool:
        o = order_status(client, order_id)
        if o and o["status"] in ("CONFIRMED", "REJECTED", "CANCELLED"):
            result.update(o)
            return True
        return False

    wait_until(probe, deadline, what=f"order {order_id} decision")
    return result


def wait_all_decided(client: httpx.Client, order_ids: list[str],
                     deadline: float = LONG_DEADLINE) -> dict[str, dict]:
    """Wait for a burst of orders to all reach a decision."""
    decided: dict[str, dict] = {}

    def probe() -> bool:
        for oid in order_ids:
            if oid in decided:
                continue
            o = order_status(client, oid)
            if o and o["status"] in ("CONFIRMED", "REJECTED", "CANCELLED"):
                decided[oid] = o
        return len(decided) == len(order_ids)

    wait_until(probe, deadline, what=f"{len(order_ids)} order decisions")
    return decided


def notifications_for(client: httpx.Client, customer_id: str) -> list[dict]:
    return get_json(client, f"/customers/{customer_id}/notifications")


def stats(client: httpx.Client) -> dict:
    return get_json(client, "/stats/orders")
