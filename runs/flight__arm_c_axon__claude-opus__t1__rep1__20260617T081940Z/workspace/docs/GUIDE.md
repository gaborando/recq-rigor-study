# Axon Framework 4.13 — Reference Pack (CQRS / Event Sourcing)

Terse reference for building a CQRS/ES backend on **Axon Framework 4.13.1** via
`axon-spring-boot-starter`, with the **embedded JPA event store** on PostgreSQL
(no Axon Server — `axon-server-connector` excluded). Spring Boot 3.5.14, Java 25.
Code-heavy.

---

## 1. Core concepts

### 1.1 CQRS / Event Sourcing in Axon
- **Command side (write):** `@Aggregate` classes handle **commands** and emit
  **events**. Aggregate state is **event-sourced** — it is rebuilt by replaying
  its events through `@EventSourcingHandler` methods; nothing else mutates state.
- **Event store:** every applied event is appended to the store. With the JPA
  engine, events are rows in `domain_event_entry` (and snapshots in
  `snapshot_event_entry`); the aggregate's `sequenceNumber` gives per-aggregate
  ordering and optimistic concurrency.
- **Query side (read):** `@EventHandler` methods (in a **projection** /
  read-model component) consume events and update query tables. `@QueryHandler`
  methods answer queries against those tables.
- **Sagas:** long-running process managers that react to events
  (`@SagaEventHandler`) and dispatch commands — used for cross-aggregate
  workflows and **compensation** (e.g. reserve → charge → confirm / reject+release).

### 1.2 What the JPA event store setup means
- The starter auto-configures a `JpaEventStorageEngine` and a JPA `TokenStore`
  using the application's `DataSource`/`EntityManager` (the same PostgreSQL the
  Spring `spring.datasource.*` points at).
- Event processors that feed projections/sagas are **Tracking Event Processors**
  (TEPs): background threads that **poll** the event store and advance a
  persisted **tracking token**. This is the source of **eventual consistency** —
  after a command commits its event, the projection updates a short time later
  when the processor polls and handles it.
- A tracking token is stored per processor; on restart the processor resumes from
  its token, so each event is **processed once per processor** (see §3.5 for the
  honest exactly-once semantics).
- Without Axon Server, command/event/query buses are the in-JVM defaults
  (`SimpleCommandBus`, `SimpleQueryBus`, the JPA-backed `EmbeddedEventStore`).

### 1.3 Processing groups
Event handlers are grouped into **processing groups** (default group name = the
package of the handler class, overridable with `@ProcessingGroup("name")`). Each
group is driven by one event processor whose mode and threading are configured
under `axon.eventhandling.processors.<group>.*`.

---

## 2. API reference (exact signatures & imports)

### 2.1 Aggregate (command side)
```java
import org.axonframework.spring.stereotype.Aggregate;          // @Aggregate (Spring stereotype bean)
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class OrderAggregate {
    @AggregateIdentifier private String orderId;   // field that holds the aggregate id
    private Status status;

    protected OrderAggregate() {}                   // REQUIRED no-arg ctor (for event-sourced rehydration)

    @CommandHandler                                 // CONSTRUCTOR handler => creates the aggregate
    public OrderAggregate(CreateOrderCommand cmd) {
        // validate, then publish a creation event:
        apply(new OrderCreatedEvent(cmd.orderId(), cmd.customerId(), cmd.productId(), cmd.quantity()));
    }

    @CommandHandler                                 // METHOD handler => routes to an existing aggregate
    public void handle(ConfirmOrderCommand cmd) {
        if (status != Status.PENDING) return;       // guard: no state change on replayed/duplicate command
        apply(new OrderConfirmedEvent(orderId, cmd.total()));
    }

    @EventSourcingHandler                           // ONLY place aggregate state is mutated
    public void on(OrderCreatedEvent e) { this.orderId = e.orderId(); this.status = Status.PENDING; }

    @EventSourcingHandler
    public void on(OrderConfirmedEvent e) { this.status = Status.CONFIRMED; }
}
```
- `AggregateLifecycle.apply(payload)` publishes the event, applies it to the
  aggregate's own `@EventSourcingHandler` synchronously, then appends it to the
  store on commit. Static import: `org.axonframework.modelling.command.AggregateLifecycle`.
- `apply(...)` may be called **only** from a command handler (or another method
  invoked during command handling), **never** from an `@EventSourcingHandler`.

### 2.2 Commands & routing
```java
import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ConfirmOrderCommand(@TargetAggregateIdentifier String orderId, int total) {}
```
- `@TargetAggregateIdentifier` marks the command field that selects the target
  aggregate instance. Required on **method** command handlers (constructor
  handlers create, so no target needed).

### 2.3 Command gateway
```java
import org.axonframework.commandhandling.gateway.CommandGateway;

CompletableFuture<R> send(Object command);          // async; R is the handler return / aggregate id
<R> R sendAndWait(Object command);                  // blocks for the result (throws on failure)
<R> R sendAndWait(Object command, long timeout, TimeUnit unit);
```

### 2.4 Events & projections (query side)
```java
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.config.ProcessingGroup;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("order-projection")
public class OrderProjection {
    @EventHandler                                   // event payload first; extra args injected (see below)
    public void on(OrderCreatedEvent e) { /* upsert read row, status=PENDING */ }

    @EventHandler
    public void on(OrderConfirmedEvent e) { /* set status=CONFIRMED, total */ }
}
```
- `@EventHandler` import: `org.axonframework.eventhandling.EventHandler`.
- Injectable handler parameters: the event payload, `@SequenceNumber long seq`,
  `@Timestamp java.time.Instant ts`, `MetaData`, and any
  `@MetaDataValue("key")`-annotated value.

### 2.5 Query handlers & gateway
```java
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;   // NOTE: messaging.responsetypes

@QueryHandler
public OrderView handle(FindOrder q) { return repo.findById(q.orderId()); }

@QueryHandler
public List<NotificationView> handle(FindNotifications q) { ... }

// dispatching:
CompletableFuture<OrderView> f =
    queryGateway.query(new FindOrder(id), ResponseTypes.instanceOf(OrderView.class));
CompletableFuture<List<NotificationView>> g =
    queryGateway.query(new FindNotifications(cid), ResponseTypes.multipleInstancesOf(NotificationView.class));
```
- `QueryGateway.query(Q query, ResponseType<R>)` → `CompletableFuture<R>`;
  overload `query(Q, Class<R>)` exists for single instance.
- `ResponseTypes.instanceOf(Class)` / `.multipleInstancesOf(Class)` —
  package `org.axonframework.messaging.responsetypes`.

### 2.6 Sagas (process managers / compensation)
```java
import org.axonframework.spring.stereotype.Saga;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;          // associateWith / end
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.beans.factory.annotation.Autowired;

@Saga
public class OrderSaga {
    @Autowired private transient CommandGateway commandGateway;   // transient: not serialized into saga state

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")            // associates saga to orderId value
    public void on(OrderCreatedEvent e) {
        SagaLifecycle.associateWith("productId", e.productId());  // add another association key
        commandGateway.send(new ReserveStockCommand(e.productId(), e.orderId(), e.quantity()));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(StockReservedEvent e) {
        commandGateway.send(new ChargeCommand(e.customerId(), e.orderId(), e.total()));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(ChargeFailedEvent e) {
        commandGateway.send(new ReleaseStockCommand(e.productId(), e.quantity()));  // COMPENSATION
        commandGateway.send(new RejectOrderCommand(e.orderId(), "INSUFFICIENT_FUNDS"));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderConfirmedEvent e) { /* terminal: saga ends */ }
}
```
- `@SagaEventHandler(associationProperty = "x")` — Axon reads field/getter `x`
  from the event and routes to the saga instance associated with that value. Use
  `keyName` when the event property name differs from the association key:
  `@SagaEventHandler(associationProperty = "orderId", keyName = "id")`.
- `SagaLifecycle.associateWith(key, value)` adds an association;
  `SagaLifecycle.end()` ends the saga programmatically (or use `@EndSaga`).
- `@StartSaga` creates a new instance when no association matches; mark the
  gateway `transient` so it is not part of the serialized saga state.

### 2.7 Exceptions / deadlines / retry
- `org.axonframework.commandhandling.CommandExecutionException` — wraps a failure
  thrown by a command handler when received via `sendAndWait`.
- `org.axonframework.modelling.command.AggregateNotFoundException` — a method
  command targeted an aggregate id with no events (never created / wrong id).
- `org.axonframework.eventsourcing.conflictresolution` /
  optimistic concurrency: a `ConcurrencyException` (sequence-number clash) is
  thrown when two commands try to append the same next sequence number; the
  losing command should be retried.
- Retry: configure a `RetryScheduler`
  (`org.axonframework.commandhandling.gateway.IntervalRetryScheduler`) on the
  `CommandGateway` for transient command failures.
- Deadlines: `@DeadlineHandler` + `DeadlineManager.schedule(Duration, name, payload)`
  (`org.axonframework.deadline.*`) for saga timeouts.

---

## 3. Wiring

### 3.1 What auto-configuration provides (JPA store, no Axon Server)
With `axon-spring-boot-starter` and `axon-server-connector` excluded, Axon
auto-configures against your Spring `DataSource`/`EntityManagerFactory`:
- `JpaEventStorageEngine` + `EmbeddedEventStore` (events in `domain_event_entry`).
- `JpaTokenStore` (`token_entry`), `JpaSagaStore` (`saga_entry`,
  `association_value_entry`).
- `SimpleCommandBus`, `SimpleQueryBus`, in-memory aggregate caching off by default.
- A Spring-transaction-integrated `TransactionManager` (each message handled in a
  Spring transaction).

### 3.2 Properties
```properties
# Drive projections/sagas as background pollers over the JPA store:
axon.eventhandling.processors.order-projection.mode=tracking
axon.eventhandling.processors.order-projection.source=eventStore
axon.eventhandling.processors.OrderSagaProcessor.mode=tracking

# Single-threaded segment is the simplest correct default (one token, in-order):
axon.eventhandling.processors.order-projection.thread-count=1
# Optional: start tracking from the head/tail
# axon.eventhandling.processors.order-projection.initial-segment-count=1

# Snapshotting threshold (optional)
axon.axonserver.enabled=false        # explicit: no Axon Server
```
- `mode=tracking` (the 4.x default for most processors) = Tracking Event
  Processor: polls, persists a token, resumes after restart. The alternative
  `subscribing` runs handlers inline in the publishing transaction (loses the
  replay/at-least-once guarantees of tokens) — prefer `tracking` here.

### 3.3 Transaction integration
Each command is handled in a Spring-managed transaction that appends events
atomically. Each tracked batch of events handled by a projection/saga also runs
in its own transaction; the token advances **in the same transaction** as the
read-model write, so a crash mid-batch replays the batch (at-least-once).

### 3.4 Sequencing policies (ordered processing)
A processor's `SequencingPolicy` decides which events may run on different
threads. Default `SequentialPerAggregatePolicy` keeps all events of one aggregate
in order on one thread (correct for per-order projections). To force total order
use `FullConcurrencyPolicy` off / a single segment (`thread-count=1`). Configure
via a `@Bean` of type `SequencingPolicy` named for the processor, or
`EventProcessingConfigurer.registerSequencingPolicy(group, cfg -> policy)`.

### 3.5 Idempotency of event handlers — honest semantics
- Tracking tokens give **exactly-once *processing per processor*** in the
  absence of failure: each event advances the token once. **On crash/retry the
  semantics are at-least-once** — a batch can be redelivered if the process dies
  after the side effect but before the token commit (or if the side effect is
  outside the token's transaction).
- Therefore: keep the read-model write **and** the token advance in the **same
  transaction** (the default for JPA), and make any **external** side effect
  (e.g. a notification row) **idempotent** independently — typically a
  `UNIQUE` constraint on the natural key (orderId) so a redelivered event cannot
  produce a second row. Do not assume "Axon = automatic exactly-once" for
  effects that aren't part of the token transaction.

---

## 4. Worked mini-flow (command → event → projection → query)

Round trip for one order id, all `com.study.app.*`:

1. `CommandGateway.send(new CreateOrderCommand(orderId, ...))`.
2. Constructor `@CommandHandler` on `OrderAggregate` validates and
   `apply(new OrderCreatedEvent(...))`.
3. The event is appended to the JPA store at sequence 0; the aggregate's
   `@EventSourcingHandler` sets `status = PENDING`.
4. The `order-projection` Tracking Event Processor polls, `@EventHandler` upserts
   the read row (status PENDING).
5. `OrderSaga` (`@StartSaga` on `OrderCreatedEvent`) drives reserve → charge,
   sending `ConfirmOrderCommand` or (on charge failure) `ReleaseStockCommand` +
   `RejectOrderCommand` as compensation.
6. The confirm/reject command appends `OrderConfirmedEvent`/`OrderRejectedEvent`;
   the projection updates status + total; a notification handler writes exactly
   one notification row (UNIQUE on orderId).
7. `QueryGateway.query(new FindOrder(orderId), ResponseTypes.instanceOf(OrderView.class))`
   returns the current read model (eventually consistent — may briefly lag).

(Full compiling code in `EXAMPLE.md`.)

---

## 5. Common pitfalls

1. **Aggregate creation must be a constructor `@CommandHandler`.** A method
   handler on a not-yet-created aggregate throws `AggregateNotFoundException`.
   The create command goes to a constructor handler; all later commands carry a
   `@TargetAggregateIdentifier` and route to method handlers.
2. **Never `apply()` inside an `@EventSourcingHandler`.** ESHs only mutate state
   and run during replay; emitting events there causes duplication/corruption.
   Decide and `apply()` in the command handler.
3. **Querying before the projection caught up.** TEPs are asynchronous; an
   immediate query after a command may see stale/missing data. Poll within the
   convergence window; do not assert synchronous read-your-writes on views.
4. **Optimistic concurrency on the aggregate.** Two commands appending the same
   next `sequenceNumber` → `ConcurrencyException`. Retry the losing command.
   Model so each invariant (a product's stock, a customer's balance) lives in a
   single aggregate so its commands serialize on that aggregate's stream.
5. **Saga association mistakes.** The `associationProperty` must name a real
   event property whose value matches an association set via `@StartSaga` or
   `SagaLifecycle.associateWith`. A wrong/missing property means the event
   reaches **no** saga instance (silently). Inject `CommandGateway` as
   `transient` so it isn't serialized into saga state.
6. **JPA store tables not created.** Axon's entities (`domain_event_entry`,
   `token_entry`, `saga_entry`, `association_value_entry`, etc.) are created only
   if Hibernate scans them and `spring.jpa.hibernate.ddl-auto` creates schema.
   The skeleton uses `ddl-auto=update`, which creates them on first boot; ensure
   Axon's JPA entities are on the classpath (they are, via the starter) and not
   excluded from entity scanning.
7. **`subscribing` vs `tracking` confusion.** Subscribing processors handle
   events inline in the publishing thread/transaction (no token, no replay);
   tracking processors poll and persist tokens. For eventual consistency +
   crash-safe replay over the JPA store, use `mode=tracking`.
8. **External side effects aren't auto-exactly-once.** See §3.5 — guard them with
   a DB unique constraint, since token semantics are at-least-once under failure.
