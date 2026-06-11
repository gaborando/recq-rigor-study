# Flight Seat Reservation System — Specification

You are building a small **flight seat-reservation backend** with per-seat
distributed locking, time-boxed holds, customer funds, and an async booking
saga. The system is exercised exclusively through the HTTP API defined here and
in `openapi.yaml`. The **acceptance test suite is the requirement**: your
implementation is done when the whole suite passes.

The suite includes concurrency scenarios. They are part of the contract: the
system must behave correctly when many requests arrive at the same time. In
particular, **a seat must never be double-booked** under any concurrency.

## Domain

### Flight
A flight has a `seatCount` (how many seats it offers) and a `seatPrice` (integer
cents charged per seat). Seats are identified by stable ids (`"1A".."30F"` style
labels derived from the seat count). Each seat is bookable by **at most one**
booking — it is **never double-booked**, regardless of timing or concurrency.
The flight view reports each seat's id and whether it is available or taken (and
by which booking when taken).

### Customer
A customer has a `name` and a `balance` (integer cents). Funds can be deposited.
Balance is **never negative** and is **never double-spent**.

### Booking
A booking references one customer, one flight, and one `seat`. The **client
supplies the booking id** (UUID) at creation; creating the same booking id again
is **idempotent** — it must not create a second booking, hold a second seat,
charge twice, or notify twice, regardless of timing or concurrency.

Booking lifecycle (asynchronous processing is allowed and expected):

```
PENDING ──> CONFIRMED                       (seat HELD AND amount charged)
       └──> REJECTED(SEAT_TAKEN)            (the seat is already held/booked; nothing charged)
       └──> REJECTED(INSUFFICIENT_FUNDS)    (seat was HELD, then RELEASED; nothing charged)
```

The booking flow is a saga:

1. **HOLD** the requested seat — this is the distributed lock. Exactly one
   concurrent requester may hold a given seat at a time. If the seat is already
   held or booked by another booking, the booking is `REJECTED(SEAT_TAKEN)` and
   nothing is charged.
2. **CHARGE** the customer the flight's `seatPrice`. On insufficient funds (or
   any charge failure) the held seat is **RELEASED** (compensation) and the
   booking is `REJECTED(INSUFFICIENT_FUNDS)`; no money is taken.
3. On success the booking is `CONFIRMED`: the seat is owned by this booking and
   the customer balance is decreased by `seatPrice` — both **exactly once**.

- A CONFIRMED booking has: exactly one seat owned by it, and the customer
  balance decreased by `seatPrice` — both **exactly once**.
- A REJECTED booking leaves **no residual effect**: any hold made during
  processing is released (compensation), and no money is taken.
- Status never regresses (e.g. CONFIRMED never becomes PENDING again).

### HOLD timeout (availability vs consistency)
A HOLD that is not CONFIRMED within a **hold-timeout** (a few seconds; short and
configurable for tests) **auto-expires** and frees the seat, so the seat becomes
bookable again by a fresh booking. No seat is ever permanently leaked: a booking
that holds a seat but cannot complete must not keep the seat held forever. A
booking whose hold expired ends `REJECTED` and frees its seat.

### Notifications
Every booking **decision** (CONFIRMED or REJECTED) produces **exactly one**
notification for the booking's customer — never zero, never two — under any
concurrency or retry pattern.

### Statistics
A statistics view reports, across all bookings: `confirmed` count, `rejected`
count, and `revenue` (sum of `seatPrice` of confirmed bookings).

## Consistency model

Reads may be **eventually consistent**: after a write, views (booking status,
stats, notifications, flight/customer views) must **converge within 10 seconds**.
The acceptance tests poll with a deadline; they never require synchronous
read-your-writes on views. Conservation must hold at convergence:

- `sum(customer balance decreases) == stats.revenue`
- `count(CONFIRMED bookings) == count(seats shown taken)` (seats sold == seats
  unavailable)
- `stats.revenue == seatPrice × count(CONFIRMED bookings)`

## API summary (normative schema in `openapi.yaml`)

| Method & path | Behavior |
|---|---|
| `POST /flights` | Create flight `{seatCount, seatPrice}` → `201 {id, seatCount, seatPrice, seats[]}` |
| `GET /flights/{id}` | → `200 {id, seatCount, seatPrice, seats:[{seat, available, bookingId?}]}` |
| `POST /customers` | Create customer `{name, balance}` → `201 {id, ...}` |
| `GET /customers/{id}` | → `200 {id, name, balance}` |
| `POST /customers/{id}/deposit` | `{amount}` adds funds → `202`. Concurrent deposits must ALL be applied. |
| `POST /bookings` | `{bookingId, customerId, flightId, seat}` → `202 {bookingId, status}`. Idempotent on `bookingId`. |
| `GET /bookings/{id}` | → `200 {bookingId, customerId, flightId, seat, status, reason?, total?}` |
| `GET /customers/{id}/notifications` | → `200 [{bookingId, status, ...}]` |
| `GET /stats/bookings` | → `200 {confirmed, rejected, revenue}` |

Errors: malformed body → `400`; unknown id on GET/POST-subresource → `404`;
`amount < 1` → `400`; `seatCount < 1`, `seatPrice < 1` → `400`; unknown `seat`
(not on the flight) on `POST /bookings` → `400` or booking ends `REJECTED`
(either accepted). Unknown `customerId`/`flightId` in `POST /bookings` → `400`
or booking ends `REJECTED` (either accepted).

## Delivery requirements (identical for every implementation)

1. Java 21, Maven, single Spring Boot deployable — start from the provided
   skeleton; do not change its build coordinates or dependency constraints.
2. Root package `com.study.app` with layer sub-packages:
   `web` (HTTP), `command` (write side), `query` (read side / views),
   `domain` (domain model), `config` (wiring). Keep code in its layer.
3. The app must serve HTTP on `${PORT}` (already configured in the skeleton)
   and expose Spring Actuator `/actuator/health` (already configured).
4. Use the provided PostgreSQL (already configured in the skeleton) for any
   persistence you need. Do not add other infrastructure.
5. **Durability (real-world deployment).** All business state — flights, seats,
   holds, customers, bookings, balances, notifications, statistics — MUST be
   persisted in the provided PostgreSQL. The system MUST recover its state after
   a process restart: stopping and restarting the application (the database
   keeps running) must not lose committed bookings, balances, or seat ownership.
   In-memory-only storage (maps, lists, caches as the source of truth) is NOT
   acceptable. This is exercised by a restart-survival check.
6. Definition of done: `mvn -B verify` succeeds, the app boots, and the
   acceptance suite in `./acceptance-tests` passes (`make test`).
