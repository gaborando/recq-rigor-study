"""Reference stub — harness validation ONLY, never a study arm.

An in-memory implementation of spec/order-inventory used to prove the
acceptance suite discriminates:

  STUB_MODE=naive    deliberate distributed-systems bugs (check-then-act races,
                     lost updates, no idempotency, duplicate notifications)
                     -> the concurrency tests MUST fail
  STUB_MODE=correct  atomic handling -> the whole suite MUST pass

Run:  STUB_MODE=correct uv run uvicorn tools.refstub.stub:app --port 8080
"""

from __future__ import annotations

import os
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor

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
LOCK = threading.RLock()  # used only in correct mode
POOL = ThreadPoolExecutor(max_workers=16)  # async order processing

products: dict[str, dict] = {}
customers: dict[str, dict] = {}
orders: dict[str, dict] = {}
notifications: dict[str, list[dict]] = {}
stat = {"confirmed": 0, "rejected": 0, "revenue": 0, "cancelled": 0}


def _maybe_lock():
    class _Noop:
        def __enter__(self):  # naive mode: no mutual exclusion
            return None
        def __exit__(self, *a):
            return False
    return _Noop() if NAIVE else LOCK


class ProductIn(BaseModel):
    name: str = Field(min_length=1)
    unitPrice: int = Field(ge=1)
    stock: int = Field(ge=0)


class CustomerIn(BaseModel):
    name: str = Field(min_length=1)
    balance: int = Field(ge=0)


class RestockIn(BaseModel):
    units: int = Field(ge=1)


class DepositIn(BaseModel):
    amount: int = Field(ge=1)


class OrderIn(BaseModel):
    orderId: str
    customerId: str
    productId: str
    quantity: int = Field(ge=1)


@app.post("/products", status_code=201)
def create_product(p: ProductIn):
    pid = str(uuid.uuid4())
    products[pid] = {"id": pid, "name": p.name, "unitPrice": p.unitPrice, "stock": p.stock}
    return products[pid]


@app.get("/products/{pid}")
def get_product(pid: str):
    if pid not in products:
        raise HTTPException(404)
    return products[pid]


@app.post("/products/{pid}/restock", status_code=202)
def restock(pid: str, body: RestockIn):
    if pid not in products:
        raise HTTPException(404)

    def apply():
        with _maybe_lock():
            current = products[pid]["stock"]   # read
            if NAIVE:
                time.sleep(RACE_WINDOW)        # ... lost-update window ...
            products[pid]["stock"] = current + body.units  # write

    POOL.submit(apply)
    return Response(status_code=202)


@app.post("/customers", status_code=201)
def create_customer(c: CustomerIn):
    cid = str(uuid.uuid4())
    customers[cid] = {"id": cid, "name": c.name, "balance": c.balance}
    notifications[cid] = []
    return customers[cid]


@app.get("/customers/{cid}")
def get_customer(cid: str):
    if cid not in customers:
        raise HTTPException(404)
    return customers[cid]


@app.post("/customers/{cid}/deposit", status_code=202)
def deposit(cid: str, body: DepositIn):
    if cid not in customers:
        raise HTTPException(404)

    def apply():
        with _maybe_lock():
            current = customers[cid]["balance"]
            if NAIVE:
                time.sleep(RACE_WINDOW)
            customers[cid]["balance"] = current + body.amount

    POOL.submit(apply)
    return Response(status_code=202)


@app.get("/customers/{cid}/notifications")
def get_notifications(cid: str):
    if cid not in customers:
        raise HTTPException(404)
    return notifications[cid]


def _process_order(oid: str):
    """The 'saga': reserve stock -> charge -> confirm, or compensate."""
    o = orders[oid]
    pid, cid, qty = o["productId"], o["customerId"], o["quantity"]
    price = products[pid]["unitPrice"]
    total = qty * price

    with _maybe_lock():
        # reserve stock (check-then-act: racy in naive mode)
        if products[pid]["stock"] < qty:
            o.update(status="REJECTED", reason="OUT_OF_STOCK")
            stat["rejected"] += 1
            notifications[cid].append({"orderId": oid, "status": "REJECTED",
                                       "reason": "OUT_OF_STOCK"})
            return
        s = products[pid]["stock"]
        if NAIVE:
            time.sleep(RACE_WINDOW)
        products[pid]["stock"] = s - qty

        # charge (double-spend window in naive mode)
        b = customers[cid]["balance"]
        if NAIVE:
            time.sleep(RACE_WINDOW)
        if b < total:
            products[pid]["stock"] += qty  # compensate reservation
            o.update(status="REJECTED", reason="INSUFFICIENT_FUNDS")
            stat["rejected"] += 1
            notifications[cid].append({"orderId": oid, "status": "REJECTED",
                                       "reason": "INSUFFICIENT_FUNDS"})
            return
        customers[cid]["balance"] = b - total

        o.update(status="CONFIRMED", total=total)
        stat["confirmed"] += 1
        stat["revenue"] += total
        notifications[cid].append({"orderId": oid, "status": "CONFIRMED"})


@app.post("/orders", status_code=202)
def place_order(o: OrderIn):
    if o.customerId not in customers or o.productId not in products:
        raise HTTPException(400)

    with _maybe_lock():
        exists = o.orderId in orders          # idempotency check
        if NAIVE:
            time.sleep(RACE_WINDOW / 5)       # ... duplicate-create window ...
        if exists:
            return orders[o.orderId]
        orders[o.orderId] = {"orderId": o.orderId, "customerId": o.customerId,
                             "productId": o.productId, "quantity": o.quantity,
                             "status": "PENDING"}
    POOL.submit(_process_order, o.orderId)
    return orders[o.orderId]


@app.get("/orders/{oid}")
def get_order(oid: str):
    if oid not in orders:
        raise HTTPException(404)
    return orders[oid]


@app.post("/orders/{oid}/cancel", status_code=202)
def cancel_order(oid: str):
    if oid not in orders:
        raise HTTPException(404)

    def apply():
        with _maybe_lock():
            o = orders[oid]
            if o["status"] != "CONFIRMED":
                return
            if NAIVE:
                time.sleep(RACE_WINDOW)
            o["status"] = "CANCELLED"
            customers[o["customerId"]]["balance"] += o["total"]
            products[o["productId"]]["stock"] += o["quantity"]
            stat["cancelled"] += 1
            stat["confirmed"] -= 0  # confirmed count unchanged by spec
            stat["revenue"] -= o["total"]
            notifications[o["customerId"]].append({"orderId": oid, "status": "CANCELLED"})

    POOL.submit(apply)
    return Response(status_code=202)


@app.get("/stats/orders")
def get_stats():
    return dict(stat)
