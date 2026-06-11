"""Reference stub — harness validation ONLY, never a study arm.

An in-memory implementation of spec/flight used to prove the acceptance suite
discriminates:

  STUB_MODE=naive    deliberate distributed-systems bugs (check-then-act seat
                     assignment with a race window, no idempotency, no hold
                     expiry, double-spend window, duplicate notifications)
                     -> the no-double-booking / duplicate / double-spend /
                        hold-expiry concurrency tests MUST fail
  STUB_MODE=correct  per-seat threading.Lock + idempotency + hold expiry via
                     timestamp -> the whole suite MUST pass

It serves the whole REST contract in a single process; the suite is black-box,
so this works for both the single and micro topologies.

Run:  STUB_MODE=correct uv run uvicorn tools.refstub.flight:app --port 8080

Env:
  HOLD_TIMEOUT_SECONDS  hold auto-expiry window (default 3) — short for tests.
"""

from __future__ import annotations

import os
import threading
import time
import uuid
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor

from fastapi import FastAPI, HTTPException, Response
from pydantic import BaseModel, Field

NAIVE = os.environ.get("STUB_MODE", "correct") == "naive"
RACE_WINDOW = 0.05  # seconds: widens check-then-act windows in naive mode
HOLD_TIMEOUT = float(os.environ.get("HOLD_TIMEOUT_SECONDS", "3"))

app = FastAPI()


from fastapi.exceptions import RequestValidationError  # noqa: E402
from fastapi.responses import JSONResponse  # noqa: E402


@app.exception_handler(RequestValidationError)
async def _validation_as_400(request, exc):
    # the spec mandates 400 for malformed bodies (FastAPI defaults to 422)
    return JSONResponse(status_code=400, content={"error": "malformed"})


GLOBAL = threading.RLock()                      # protects dict membership / stats
SEAT_LOCKS: dict[tuple[str, str], threading.Lock] = {}   # per-seat lock (correct mode)
_SEAT_LOCK_GUARD = threading.Lock()
POOL = ThreadPoolExecutor(max_workers=16)       # async booking processing

flights: dict[str, dict] = {}
# seat ownership: (flightId, seat) -> {"bookingId": str, "confirmed": bool, "heldAt": float}
seat_owner: dict[tuple[str, str], dict] = {}
customers: dict[str, dict] = {}
bookings: dict[str, dict] = {}
notifications: dict[str, list[dict]] = {}
stat = {"confirmed": 0, "rejected": 0, "revenue": 0}

_LETTERS = "ABCDEF"


def _seat_label(i: int) -> str:
    return f"{i // len(_LETTERS) + 1}{_LETTERS[i % len(_LETTERS)]}"


def _seat_lock(flight_id: str, seat: str) -> threading.Lock:
    key = (flight_id, seat)
    with _SEAT_LOCK_GUARD:
        lk = SEAT_LOCKS.get(key)
        if lk is None:
            lk = threading.Lock()
            SEAT_LOCKS[key] = lk
        return lk


def _hold_active(rec: dict) -> bool:
    """Is a seat ownership record still blocking the seat? Confirmed always
    blocks; an unconfirmed hold blocks only until the timeout (correct mode)."""
    if rec["confirmed"]:
        return True
    if NAIVE:
        return True  # naive mode never expires holds -> seat can leak forever
    return (time.monotonic() - rec["heldAt"]) < HOLD_TIMEOUT


def _seat_taken(flight_id: str, seat: str) -> bool:
    rec = seat_owner.get((flight_id, seat))
    return rec is not None and _hold_active(rec)


class FlightIn(BaseModel):
    seatCount: int = Field(ge=1)
    seatPrice: int = Field(ge=1)


class CustomerIn(BaseModel):
    name: str = Field(min_length=1)
    balance: int = Field(ge=0)


class DepositIn(BaseModel):
    amount: int = Field(ge=1)


class BookingIn(BaseModel):
    bookingId: str
    customerId: str
    flightId: str
    seat: str


class ChangeSeatIn(BaseModel):
    newSeat: str


@app.post("/flights", status_code=201)
def create_flight(f: FlightIn):
    fid = str(uuid.uuid4())
    seats = [_seat_label(i) for i in range(f.seatCount)]
    flights[fid] = {"id": fid, "seatCount": f.seatCount, "seatPrice": f.seatPrice,
                    "seatList": seats}
    return _flight_view(fid)


def _flight_view(fid: str) -> dict:
    f = flights[fid]
    seats = []
    for s in f["seatList"]:
        rec = seat_owner.get((fid, s))
        if rec and _hold_active(rec):
            entry = {"seat": s, "available": False}
            if rec["confirmed"]:
                entry["bookingId"] = rec["bookingId"]
            seats.append(entry)
        else:
            seats.append({"seat": s, "available": True})
    return {"id": fid, "seatCount": f["seatCount"], "seatPrice": f["seatPrice"],
            "seats": seats}


@app.get("/flights/{fid}")
def get_flight(fid: str):
    if fid not in flights:
        raise HTTPException(404)
    return _flight_view(fid)


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
        with (GLOBAL if not NAIVE else _noop()):
            current = customers[cid]["balance"]
            if NAIVE:
                time.sleep(RACE_WINDOW)        # ... lost-update window ...
            customers[cid]["balance"] = current + body.amount

    POOL.submit(apply)
    return Response(status_code=202)


class _Noop:
    def __enter__(self):
        return None
    def __exit__(self, *a):
        return False


def _noop():
    return _Noop()


@app.get("/customers/{cid}/notifications")
def get_notifications(cid: str):
    if cid not in customers:
        raise HTTPException(404)
    return notifications[cid]


def _notify(cid: str, payload: dict):
    notifications[cid].append(payload)


def _process_booking(bid: str):
    """The saga: HOLD seat (the distributed lock) -> charge -> confirm, or
    compensate (release the seat)."""
    b = bookings[bid]
    fid, cid, seat = b["flightId"], b["customerId"], b["seat"]
    price = flights[fid]["seatPrice"]
    key = (fid, seat)

    # --- HOLD the seat (distributed lock) ---
    if NAIVE:
        # check-then-act with a race window: two racers can both "win" the seat.
        taken = _seat_taken(fid, seat)
        time.sleep(RACE_WINDOW)
        if taken:
            _reject(b, cid, "SEAT_TAKEN")
            return
        seat_owner[key] = {"bookingId": bid, "confirmed": False, "heldAt": time.monotonic()}
    else:
        with _seat_lock(fid, seat):
            if _seat_taken(fid, seat):
                _reject(b, cid, "SEAT_TAKEN")
                return
            seat_owner[key] = {"bookingId": bid, "confirmed": False,
                               "heldAt": time.monotonic()}

    # --- CHARGE the customer ---
    if NAIVE:
        bal = customers[cid]["balance"]
        time.sleep(RACE_WINDOW)               # double-spend window
        if bal < price:
            del seat_owner[key]               # compensate (release hold)
            _reject(b, cid, "INSUFFICIENT_FUNDS")
            return
        customers[cid]["balance"] = bal - price
    else:
        with GLOBAL:
            bal = customers[cid]["balance"]
            if bal < price:
                # compensate: release the held seat (only if still ours)
                with _seat_lock(fid, seat):
                    rec = seat_owner.get(key)
                    if rec and rec["bookingId"] == bid and not rec["confirmed"]:
                        del seat_owner[key]
                _reject(b, cid, "INSUFFICIENT_FUNDS")
                return
            customers[cid]["balance"] = bal - price

    # --- CONFIRM ---
    if NAIVE:
        seat_owner[key]["confirmed"] = True
        _confirm(b, cid, price)
    else:
        with _seat_lock(fid, seat):
            rec = seat_owner.get(key)
            if rec is None or rec["bookingId"] != bid:
                # hold expired before confirm: refund + reject (no leaked charge)
                with GLOBAL:
                    customers[cid]["balance"] += price
                _reject(b, cid, "SEAT_TAKEN")
                return
            rec["confirmed"] = True
        _confirm(b, cid, price)


def _confirm(b: dict, cid: str, price: int):
    with GLOBAL:
        if b["status"] != "PENDING":
            return
        b.update(status="CONFIRMED", total=price)
        stat["confirmed"] += 1
        stat["revenue"] += price
        _notify(cid, {"bookingId": b["bookingId"], "status": "CONFIRMED"})


def _reject(b: dict, cid: str, reason: str):
    with GLOBAL:
        if b["status"] != "PENDING":
            return
        b.update(status="REJECTED", reason=reason)
        stat["rejected"] += 1
        _notify(cid, {"bookingId": b["bookingId"], "status": "REJECTED", "reason": reason})


@app.post("/bookings", status_code=202)
def place_booking(o: BookingIn):
    if o.customerId not in customers or o.flightId not in flights:
        raise HTTPException(400)
    if o.seat not in flights[o.flightId]["seatList"]:
        raise HTTPException(400)

    if NAIVE:
        exists = o.bookingId in bookings       # no real idempotency guard
        time.sleep(RACE_WINDOW / 5)            # duplicate-create window
        if exists:
            return bookings[o.bookingId]
        bookings[o.bookingId] = {"bookingId": o.bookingId, "customerId": o.customerId,
                                 "flightId": o.flightId, "seat": o.seat, "status": "PENDING"}
    else:
        with GLOBAL:
            if o.bookingId in bookings:
                return bookings[o.bookingId]
            bookings[o.bookingId] = {"bookingId": o.bookingId, "customerId": o.customerId,
                                     "flightId": o.flightId, "seat": o.seat, "status": "PENDING"}
    POOL.submit(_process_booking, o.bookingId)
    return bookings[o.bookingId]


@app.get("/bookings/{bid}")
def get_booking(bid: str):
    if bid not in bookings:
        raise HTTPException(404)
    return bookings[bid]


@app.post("/bookings/{bid}/change-seat", status_code=202)
def change_seat(bid: str, body: ChangeSeatIn):
    if bid not in bookings:
        raise HTTPException(404)

    def apply():
        b = bookings[bid]
        if b["status"] != "CONFIRMED":
            return
        fid = b["flightId"]
        old = b["seat"]
        new = body.newSeat
        if new == old:
            return                              # no-op success
        if new not in flights[fid]["seatList"]:
            return
        old_key, new_key = (fid, old), (fid, new)
        if NAIVE:
            if _seat_taken(fid, new):
                return                          # keep old seat
            time.sleep(RACE_WINDOW)             # two racers can both move in
            seat_owner[new_key] = {"bookingId": bid, "confirmed": True,
                                   "heldAt": time.monotonic()}
            seat_owner.pop(old_key, None)
            b["seat"] = new
        else:
            # two-seat lock in a stable order to avoid deadlock
            first, second = sorted([old, new])
            with _seat_lock(fid, first), _seat_lock(fid, second):
                if _seat_taken(fid, new):
                    return                      # target taken -> retain old
                seat_owner[new_key] = {"bookingId": bid, "confirmed": True,
                                       "heldAt": time.monotonic()}
                rec = seat_owner.get(old_key)
                if rec and rec["bookingId"] == bid:
                    del seat_owner[old_key]
                b["seat"] = new

    POOL.submit(apply)
    return Response(status_code=202)


@app.get("/stats/bookings")
def get_stats():
    return {"confirmed": stat["confirmed"], "rejected": stat["rejected"],
            "revenue": stat["revenue"]}
