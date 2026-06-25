# Axon — Distributed services via Axon Server

Deliver four independent Axon services in the reactor: `edge`, `orders`,
`inventory`, `customers`. Each is its own Spring Boot app with its own database;
all connect to the **same Axon Server** (`axon.axonserver.servers` is pre-wired
from `AXON_HOST`/`AXON_PORT`). Axon Server provides distributed command routing,
event distribution, and query routing across services.

Shared messages (commands/events/queries) live in the **`api` module**; every
service depends on it. With Axon Server connected:
- A `@CommandHandler` for a command lives in exactly one service; sending that
  command from another service routes to the owner automatically.
- Events published by any aggregate are distributed to all subscribing
  `@EventHandler` processors across services (tracking processors over the
  distributed event store / Axon Server).
- `@QueryHandler`s are reachable cross-service via the `QueryGateway`.

Suggested split:
- `edge`: REST controllers that send commands / dispatch queries via the
  gateways; no aggregates, no projections.
- `orders`: order `@Aggregate` + the saga (`@Saga` / `@SagaEventHandler`,
  `associationProperty`) coordinating reserve→charge→confirm/compensate + stats
  projection.
- `inventory`: product `@Aggregate` + stock projection.
- `customers`: customer `@Aggregate` + notifications projection.

What Axon Server / the framework give you across services:
- Aggregate optimistic concurrency (sequence-number conflicts) — no lost updates
  on a single aggregate.
- Exactly-once **event processing** per tracking processor (tracking tokens):
  a restarted projection resumes from its token and rebuilds.
- Saga lifecycle persistence (saga store) so a crash mid-saga resumes.

Be honest about idempotency: command de-duplication for client retries is YOUR
responsibility (e.g. check aggregate existence on a create, or key on the
client-supplied id). The single-service GUIDE.md/EXAMPLE.md API reference
applies; micro just distributes the aggregates/projections across services and
relies on Axon Server instead of the embedded event bus.
