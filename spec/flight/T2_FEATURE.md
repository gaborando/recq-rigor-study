# T2 Evolution Task — Seat Change

Extend the existing system (your own previous implementation) with **seat
change** on a confirmed booking. The T2 acceptance suite (a superset of the
original suite) is the requirement.

## Behavior

- `POST /bookings/{id}/change-seat` `{newSeat}` → `202` (idempotent; replays are
  harmless).
- Only a **CONFIRMED** booking can change its seat. Changing the seat of a
  PENDING or REJECTED booking has no effect (the request is still `202`-accepted;
  the booking keeps its state — `409` is also acceptable for those cases).
- A seat change **atomically releases the old seat and acquires the new one** —
  this is a two-seat distributed lock. Either both happen or neither does:
  - If `newSeat` is **free**, the booking now owns `newSeat`, the **old seat is
    released** (becomes available), and the booking stays CONFIRMED on the new
    seat. Exactly **one** seat is held by the booking afterwards.
  - If `newSeat` is **already taken** (held or booked by another booking), the
    change **fails**: the booking **retains its OLD seat** (no seat lost, no
    double-hold), stays CONFIRMED on the old seat. (`409` is acceptable, or
    `202`-accept then the booking simply keeps the old seat — either way the old
    seat is retained.)
  - Changing to the **same seat** the booking already owns is a no-op success.
- No charge or refund occurs on a seat change: the price is the same per seat,
  so the customer balance and `stats` (`confirmed`, `rejected`, `revenue`) are
  **unaffected**.
- Idempotent and exactly-once: N concurrent change requests to the same
  `newSeat` for one booking result in exactly one effect; the booking owns
  exactly one seat afterwards.
- Conservation still holds at convergence (10 s deadline): seats sold == seats
  shown taken, and no seat is ever owned by two bookings or lost.

## Concurrency scenarios added by the T2 suite

1. N concurrent `change-seat` calls on one CONFIRMED booking to the same free
   `newSeat` → the booking ends owning exactly one seat (the new one), the old
   seat is freed exactly once, no double-hold.
2. Two different CONFIRMED bookings concurrently try to change INTO the **same**
   target seat → **at most one succeeds**; the loser retains its old seat. The
   target seat is owned by at most one booking; no seat is lost.
3. Conservation audit across a burst of mixed book/change-seat traffic: seats
   sold == seats taken, money unchanged by changes.

## Delivery requirements

Unchanged from SPEC.md. Modify your existing codebase in place; the diff between
your starting tree and your final tree is part of the measurement.
