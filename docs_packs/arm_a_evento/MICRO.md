# Evento — Multi-Bundle (microservices) wiring

When the task asks for independent services, deliver **four Evento bundles** in
the provided Maven reactor: `edge`, `orders`, `inventory`, `customers`. Each is
its own Spring Boot app with its own `EventoConfiguration` (pre-wired) connecting
to the **same Evento server** — the server is the integration fabric.

Key points (verified against the framework's `evento-lab-microservices`):

- **Shared payloads live in the `api` module.** Put every command/event/query/
  view there; each service depends on `api`. A command defined in `api` can be
  *sent* from one bundle and *handled* in another — the server routes it.
- **Each bundle scans only its own package tree** (`setBasePackage` is pre-wired
  to that module). So place a component in the service that owns it:
  - `orders`: the order `@Aggregate`, the reserve→charge→confirm `@Saga`, the
    stats `@Projector`/`@Projection`.
  - `inventory`: product/stock `@Aggregate` (+ its projection).
  - `customers`: balance `@Aggregate` and the notifications `@Projector`.
  - `edge`: only `@Invoker` + a `@RestController` that calls
    `eventoBundle.getInvoker(...)`; it holds NO aggregates and NO database.
- **Cross-bundle interaction is just gateways.** A saga in `orders` sends a
  `ReserveStockCommand` (handled in `inventory`) and a `ChargeCommand` (handled
  in `customers`) via the injected `CommandGateway`; events flow back through the
  server to whichever bundle subscribes. You do NOT write HTTP calls between
  services — the broker carries commands/events/queries.
- **What you get for free across the network:** ordered, exactly-once event
  consumption per consumer; single-active consumption per context; replayable
  event log (a restarted consumer rebuilds its read model). You do not hand-roll
  idempotency, retries, or saga checkpoints — model them as RECQ components.
- **Durable by default.** The pre-wired config of the stateful services
  (`orders`/`inventory`/`customers`) uses the **JDBC consumer state store**
  backed by that service's own Postgres — consumer checkpoints, saga state,
  dead-letter queue and observer dedup all survive a restart (Flyway creates
  the `evento_v2_*` tables automatically). Any read-model/projection state you
  add must likewise be persisted in that service's Postgres, not in memory.
  `edge` is stateless (Invoker only) — no database, no consumer store.

The single-bundle GUIDE.md/EXAMPLE.md annotation reference still applies
verbatim — micro just distributes the same components across four bundles.
