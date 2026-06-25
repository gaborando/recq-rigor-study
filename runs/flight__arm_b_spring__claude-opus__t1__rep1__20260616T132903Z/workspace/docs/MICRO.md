# Plain Spring — Multi-service wiring (no broker)

Deliver four independent Spring Boot services in the reactor: `edge`, `orders`,
`inventory`, `customers`. Each has its **own database** (`*_DB_URL`); a service
may NOT connect to another service's database. There is **no message broker** —
services talk over **HTTP/REST**, and you must build the distributed-correctness
machinery yourself.

Peer addresses are injected as env/properties: `ORDERS_URL`, `INVENTORY_URL`,
`CUSTOMERS_URL`, `EDGE_URL`. Use a `RestClient`/`WebClient` (or `RestTemplate`)
to call peers.

Suggested split:
- `edge`: the public REST contract; forwards each call to the owning service.
- `orders`: order lifecycle + the reserve→charge→confirm/reject saga + stats.
- `inventory`: product/stock with reservation/release.
- `customers`: balances + notifications.

What you must implement explicitly (the broker does none of it for you):
- **Idempotency across the wire.** A client-supplied `orderId` retried or raced
  must produce one effect — use a unique constraint / idempotency key persisted
  in the owning service; make peer calls idempotent too (e.g. a reservation id).
- **Saga + compensation over REST.** `orders` calls `inventory` to reserve, then
  `customers` to charge; on charge failure it must call `inventory` to release.
  Decide sync vs async; either way, a crash between steps must not leak a
  reservation or double-charge — persist saga state and make steps replayable.
- **No lost updates.** Concurrent stock/balance mutations need DB-level safety
  (atomic `UPDATE ... SET x = x ± ?`, `@Version`, or `SELECT ... FOR UPDATE`).
- **Durability.** State must live in Postgres, not memory — services can be
  restarted at any time and must recover committed state.
- **Eventual consistency.** Cross-service reads (stats, notifications) may lag;
  converge within the deadline.

The single-service GUIDE.md/EXAMPLE.md patterns (transactions, locking,
idempotency keys, async) all apply — now you also own the cross-service edges.
