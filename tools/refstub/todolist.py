"""Reference stub — harness validation ONLY, never a study arm.

An in-memory implementation of spec/todolist used to prove the acceptance suite
discriminates:

  STUB_MODE=naive    deliberate distributed-systems bugs (check-then-act
                     completion detection with a race window, no idempotency,
                     duplicate completion notifications)
                     -> the concurrency tests MUST fail
  STUB_MODE=correct  a per-list lock guards check+completion-detection as one
                     atomic critical section + idempotency
                     -> the whole suite MUST pass

Single-process; serves the whole REST contract (black-box, works for both
topologies). Run:
    STUB_MODE=correct uv run uvicorn tools.refstub.todolist:app --port 8080
"""

from __future__ import annotations

import os
import threading
import time
from collections import defaultdict

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel, Field

NAIVE = os.environ.get("STUB_MODE", "correct") == "naive"
RACE_WINDOW = 0.05  # seconds: widens check-then-act windows in naive mode

app = FastAPI()


@app.exception_handler(Exception)
async def _ignore(request, exc):  # pragma: no cover - never expected
    raise exc


from fastapi.exceptions import RequestValidationError  # noqa: E402
from fastapi.responses import JSONResponse  # noqa: E402


@app.exception_handler(RequestValidationError)
async def _validation_as_400(request, exc):
    # the spec mandates 400 for malformed bodies (FastAPI defaults to 422)
    return JSONResponse(status_code=400, content={"error": "malformed"})


# A single global lock would over-serialize; the interesting bug is per-list
# completion detection, so correct mode locks PER LIST.
_GLOBAL = threading.RLock()
_locks: dict[str, threading.RLock] = defaultdict(threading.RLock)

# state ------------------------------------------------------------------
# lists[lid] = {"listId","name","status","items": {itemId: {"itemId","content","checked"}},
#               "order": [itemId...]}
lists: dict[str, dict] = {}
notifications: dict[str, list[dict]] = {}


class _Noop:
    def __enter__(self):  # naive mode: no mutual exclusion
        return None

    def __exit__(self, *a):
        return False


def _list_lock(lid: str):
    """Per-list critical section (correct mode); no-op in naive mode."""
    if NAIVE:
        return _Noop()
    with _GLOBAL:
        return _locks[lid]


def _items(lst: dict) -> list[dict]:
    return [lst["items"][i] for i in lst["order"] if i in lst["items"]]


def _all_checked(lst: dict) -> bool:
    its = _items(lst)
    return len(its) > 0 and all(it["checked"] for it in its)


def _reconcile_completion(lst: dict) -> None:
    """Recompute status from items; on an ACTIVE -> COMPLETED transition emit
    EXACTLY ONE notification. Callers MUST hold the per-list lock (correct mode).

    In naive mode this runs without the lock and with a sleep between the
    "should be completed?" check and the status write, so two concurrent final
    checks both observe ACTIVE-not-yet-completed and both emit a notification
    (or, on other interleavings, none) -> the discriminating bug."""
    should_complete = _all_checked(lst)
    was_completed = lst["status"] == "COMPLETED"

    if NAIVE:
        time.sleep(RACE_WINDOW)  # ... check-then-act window ...

    if should_complete and not was_completed:
        lst["status"] = "COMPLETED"
        notifications[lst["listId"]].append({"listId": lst["listId"], "status": "COMPLETED"})
    elif not should_complete and was_completed:
        lst["status"] = "ACTIVE"


# models -----------------------------------------------------------------
class ListIn(BaseModel):
    listId: str
    name: str = Field(min_length=1)


class ItemIn(BaseModel):
    itemId: str
    content: str = Field(min_length=1)


class RenameIn(BaseModel):
    content: str = Field(min_length=1)


# routes -----------------------------------------------------------------
@app.post("/lists", status_code=202)
def create_list(body: ListIn):
    with _list_lock(body.listId):
        exists = body.listId in lists
        if NAIVE:
            time.sleep(RACE_WINDOW / 5)  # duplicate-create window
        if exists:
            lst = lists[body.listId]
            return {"listId": lst["listId"], "name": lst["name"], "status": lst["status"]}
        lists[body.listId] = {"listId": body.listId, "name": body.name,
                              "status": "ACTIVE", "items": {}, "order": []}
        notifications[body.listId] = []
    return {"listId": body.listId, "name": body.name, "status": "ACTIVE"}


@app.get("/lists/{lid}")
def get_list(lid: str):
    if lid not in lists:
        raise HTTPException(404)
    lst = lists[lid]
    return {"listId": lst["listId"], "name": lst["name"], "status": lst["status"],
            "items": [dict(it) for it in _items(lst)]}


@app.post("/lists/{lid}/items", status_code=202)
def add_item(lid: str, body: ItemIn):
    if lid not in lists:
        raise HTTPException(404)
    with _list_lock(lid):
        lst = lists[lid]
        exists = body.itemId in lst["items"]
        if NAIVE:
            time.sleep(RACE_WINDOW / 5)
        if not exists:
            lst["items"][body.itemId] = {"itemId": body.itemId,
                                         "content": body.content, "checked": False}
            lst["order"].append(body.itemId)
        _reconcile_completion(lst)  # a fresh unchecked item un-completes
    return Response(status_code=202)


def _require_item(lid: str, item_id: str) -> dict:
    if lid not in lists:
        raise HTTPException(404)
    if item_id not in lists[lid]["items"]:
        raise HTTPException(404)
    return lists[lid]


@app.put("/lists/{lid}/items/{item_id}/check", status_code=202)
def check_item(lid: str, item_id: str):
    _require_item(lid, item_id)
    with _list_lock(lid):
        lst = lists[lid]
        if item_id not in lst["items"]:  # re-check after lock
            raise HTTPException(404)
        lst["items"][item_id]["checked"] = True
        if NAIVE:
            # widen the window so two concurrent final checks both set their flag
            # BEFORE either recomputes completion -> both observe all-checked /
            # not-yet-completed and both emit -> the discriminating double-completion.
            time.sleep(RACE_WINDOW)
        _reconcile_completion(lst)
    return Response(status_code=202)


@app.put("/lists/{lid}/items/{item_id}/uncheck", status_code=202)
def uncheck_item(lid: str, item_id: str):
    _require_item(lid, item_id)
    with _list_lock(lid):
        lst = lists[lid]
        if item_id not in lst["items"]:
            raise HTTPException(404)
        lst["items"][item_id]["checked"] = False
        _reconcile_completion(lst)
    return Response(status_code=202)


@app.put("/lists/{lid}/items/{item_id}/rename", status_code=202)
def rename_item(lid: str, item_id: str, body: RenameIn):
    _require_item(lid, item_id)
    with _list_lock(lid):
        lst = lists[lid]
        if item_id not in lst["items"]:
            raise HTTPException(404)
        cur = lst["items"][item_id]["content"]
        if NAIVE:
            time.sleep(RACE_WINDOW)  # lost-update window vs. concurrent check
            lst["items"][item_id] = {"itemId": item_id, "content": body.content,
                                     "checked": lst["items"][item_id]["checked"]}
        else:
            lst["items"][item_id]["content"] = body.content
        _ = cur
    return Response(status_code=202)


@app.delete("/lists/{lid}/items/{item_id}", status_code=202)
def delete_item(lid: str, item_id: str):
    # T2: idempotent; deleting may trigger completion (last unchecked removed).
    if lid not in lists:
        raise HTTPException(404)
    with _list_lock(lid):
        lst = lists[lid]
        if item_id in lst["items"]:
            del lst["items"][item_id]
            lst["order"] = [i for i in lst["order"] if i != item_id]
        _reconcile_completion(lst)  # emptied -> ACTIVE; last-unchecked-gone -> COMPLETED
    return Response(status_code=202)


@app.get("/lists/{lid}/notifications")
def get_notifications(lid: str):
    if lid not in lists:
        raise HTTPException(404)
    return notifications[lid]


@app.get("/stats/lists")
def get_stats():
    active = completed = total_items = checked_items = 0
    for lst in lists.values():
        if lst["status"] == "COMPLETED":
            completed += 1
        else:
            active += 1
        for it in _items(lst):
            total_items += 1
            if it["checked"]:
                checked_items += 1
    return {"active": active, "completed": completed,
            "totalItems": total_items, "checkedItems": checked_items}
