# Order & Inventory System — Specification

You are building a small **order-management backend** with inventory reservation,
customer funds, and order processing. The system is exercised exclusively through
the HTTP API defined here and in `openapi.yaml`. The **acceptance test suite is
the requirement**: your implementation is done when the whole suite passes.

The suite includes concurrency scenarios. They are part of the contract: the
system must behave correctly when many requests arrive at the same time.

## Domain

### Product
A product has a `name`, a `unitPrice` (integer cents), and a `stock` (units on
hand). Stock can be replenished. Stock is **never negative** and is **never
oversold**: units are reserved/consumed at most once.

### Customer
A customer has a `name` and a `balance` (integer cents). Funds can be deposited.
Balance is **never negative** and is **never double-spent**.

### Order
An order references one customer, one product, and a `quantity` (≥ 1).
The **client supplies the order id** (UUID) at creation; creating the same order
id again is **idempotent** — it must not create a second order, charge twice, or
notify twice, regardless of timing or concurrency.

Order lifecycle (asynchronous processing is allowed and expected):

```
PENDING ──> CONFIRMED                       (stock reserved AND amount charged)
       └──> REJECTED(OUT_OF_STOCK)          (insufficient stock; nothing charged)
       └──> REJECTED(INSUFFICIENT_FUNDS)    (stock was reserved, then released; nothing charged)
```

- Order total = `quantity × product.unitPrice` at processing time.
- A CONFIRMED order has: product stock decreased by `quantity` and customer
  balance decreased by the total — both **exactly once**.
- A REJECTED order leaves **no residual effect**: any reservation made during
  processing is released (compensation), and no money is taken.
- Status never regresses (e.g. CONFIRMED never becomes PENDING again).

### Notifications
Every order **decision** (CONFIRMED or REJECTED) produces **exactly one**
notification for the order's customer — never zero, never two — under any
concurrency or retry pattern.

### Statistics
A statistics view reports, across all orders: `confirmed` count, `rejected`
count, and `revenue` (sum of totals of confirmed orders).

## Consistency model

Reads may be **eventually consistent**: after a write, views (order status,
stats, notifications, product/customer views) must **converge within 10
seconds**. The acceptance tests poll with a deadline; they never require
synchronous read-your-writes on views. Conservation must hold at convergence:

- `sum(customer balance decreases) == stats.revenue`
- `initial stock + restocks == remaining stock + sum(quantity of CONFIRMED orders)`

## API summary (normative schema in `openapi.yaml`)

| Method & path | Behavior |
|---|---|
| `POST /products` | Create product `{name, unitPrice, stock}` → `201 {id, ...}` |
| `GET /products/{id}` | → `200 {id, name, unitPrice, stock}` (stock = on hand, minus reserved/confirmed) |
| `POST /products/{id}/restock` | `{units}` adds stock → `202`. Concurrent restocks must ALL be applied. |
| `POST /customers` | Create customer `{name, balance}` → `201 {id, ...}` |
| `GET /customers/{id}` | → `200 {id, name, balance}` |
| `POST /customers/{id}/deposit` | `{amount}` adds funds → `202`. Concurrent deposits must ALL be applied. |
| `POST /orders` | `{orderId, customerId, productId, quantity}` → `202 {orderId, status}`. Idempotent on `orderId`. |
| `GET /orders/{id}` | → `200 {orderId, customerId, productId, quantity, status, reason?, total?}` |
| `GET /customers/{id}/notifications` | → `200 [{orderId, status, ...}]` |
| `GET /stats/orders` | → `200 {confirmed, rejected, revenue}` |

Errors: malformed body → `400`; unknown id on GET/POST-subresource → `404`;
`quantity < 1`, `units < 1`, `amount < 1` → `400`. Unknown `customerId`/
`productId` in `POST /orders` → `400` or order ends `REJECTED` (either accepted).

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
5. **Durability (real-world deployment).** All business state — products,
   customers, orders, balances, stock, notifications, statistics — MUST be
   persisted in the provided PostgreSQL. The system MUST recover its state
   after a process restart: stopping and restarting the application (the
   database keeps running) must not lose committed orders, balances, or stock.
   In-memory-only storage (maps, lists, caches as the source of truth) is NOT
   acceptable. This is exercised by a restart-survival check.
6. Definition of done: `mvn -B verify` succeeds, the app boots, and the
   acceptance suite in `./acceptance-tests` passes (`make test`).
