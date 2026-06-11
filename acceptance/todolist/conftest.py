"""Shared fixtures/helpers for the collaborative-todolist acceptance suite.

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
LONG_DEADLINE = 3 * DEADLINE  # for burst convergence (many async completions)

_seed = os.environ.get("RNG_SEED")
rng = random.Random(int(_seed)) if _seed else random.Random()


@pytest.fixture(scope="session")
def client() -> httpx.Client:
    with httpx.Client(base_url=BASE_URL, timeout=30.0) as c:
        yield c


# ---------------------------------------------------------------- helpers

def rand_name(prefix: str) -> str:
    return f"{prefix}-" + "".join(rng.choices(string.ascii_lowercase + string.digits, k=10))


def new_id() -> str:
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

def create_list(client: httpx.Client, list_id: str | None = None) -> str:
    lid = list_id or new_id()
    r = client.post("/lists", json={"listId": lid, "name": rand_name("list")})
    assert r.status_code in (200, 202), f"POST /lists -> {r.status_code}: {r.text[:200]}"
    return lid


def add_item(client: httpx.Client, list_id: str, item_id: str | None = None,
             content: str | None = None) -> str:
    iid = item_id or new_id()
    r = client.post(f"/lists/{list_id}/items",
                    json={"itemId": iid, "content": content or rand_name("item")})
    assert r.status_code in (200, 202), f"POST item -> {r.status_code}: {r.text[:200]}"
    return iid


def check_item(client: httpx.Client, list_id: str, item_id: str) -> httpx.Response:
    return client.put(f"/lists/{list_id}/items/{item_id}/check")


def uncheck_item(client: httpx.Client, list_id: str, item_id: str) -> httpx.Response:
    return client.put(f"/lists/{list_id}/items/{item_id}/uncheck")


def rename_item(client: httpx.Client, list_id: str, item_id: str, content: str) -> httpx.Response:
    return client.put(f"/lists/{list_id}/items/{item_id}/rename", json={"content": content})


def delete_item(client: httpx.Client, list_id: str, item_id: str) -> httpx.Response:
    return client.delete(f"/lists/{list_id}/items/{item_id}")


def list_view(client: httpx.Client, list_id: str) -> dict | None:
    r = client.get(f"/lists/{list_id}")
    if r.status_code == 404:  # allowed only before the write converges
        return None
    assert r.status_code == 200, f"GET /lists/{list_id} -> {r.status_code}: {r.text[:200]}"
    return r.json()


def list_status(client: httpx.Client, list_id: str) -> str | None:
    v = list_view(client, list_id)
    return v["status"] if v else None


def items_of(client: httpx.Client, list_id: str) -> list[dict]:
    v = list_view(client, list_id)
    return v["items"] if v else []


def make_list_with_items(client: httpx.Client, n: int, *, checked: int = 0) -> tuple[str, list[str]]:
    """Create a list with n items; the first `checked` of them already checked.
    Returns (listId, [itemId...]). Waits until the list view shows all n items."""
    lid = create_list(client)
    iids = [add_item(client, lid) for _ in range(n)]
    for iid in iids[:checked]:
        check_item(client, lid, iid)

    def settled() -> bool:
        v = list_view(client, lid)
        if not v or len(v["items"]) != n:
            return False
        by_id = {it["itemId"]: it for it in v["items"]}
        return all(by_id[i]["checked"] for i in iids[:checked])

    wait_until(settled, what=f"list {lid} has {n} items ({checked} checked)")
    return lid, iids


def wait_status(client: httpx.Client, list_id: str, status: str,
                deadline: float = DEADLINE) -> None:
    wait_until(lambda: list_status(client, list_id) == status,
               deadline=deadline, what=f"list {list_id} status == {status}")


def notifications_for(client: httpx.Client, list_id: str) -> list[dict]:
    return get_json(client, f"/lists/{list_id}/notifications")


def completion_count(client: httpx.Client, list_id: str) -> int:
    return sum(1 for n in notifications_for(client, list_id) if n["status"] == "COMPLETED")


def stats(client: httpx.Client) -> dict:
    return get_json(client, "/stats/lists")
