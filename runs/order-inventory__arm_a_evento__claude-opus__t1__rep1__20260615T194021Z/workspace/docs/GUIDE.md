# Evento Framework — Developer Guide (v2.1.0)

Evento is a JVM (Java 17+) framework for building event-driven, CQRS / event-sourced
applications structured by the **RECQ component pattern**. A *bundle* is one deployable
unit (a Spring Boot app, in the integration pattern) that registers components; bundles
talk to an **Evento Server** (the message bus / event store) over a TCP transport.
Components never call each other directly — they exchange **messages** (commands,
events, queries) through gateways, and the platform routes them.

All annotations live under `com.evento.common.modeling.annotations`; payload base
classes under `com.evento.common.modeling.messaging.payload`; query response wrappers
under `com.evento.common.modeling.messaging.query`; state base classes under
`com.evento.common.modeling.state`; gateways under `com.evento.common.messaging.gateway`.

---

## 1. Core concepts

### The seven RECQ component roles

A component is a class annotated with exactly one of the role annotations below. It is
discovered by classpath scanning under the bundle base package and instantiated through
the configured injector (Spring `BeanFactory::getBean` in the integration pattern).

| Role | Annotation | Side | Holds state? | May emit | Handler annotation(s) |
|------|-----------|------|--------------|----------|-----------------------|
| **Invoker** | `@Invoker` | edge | no | commands + queries (via gateways) | `@InvocationHandler` |
| **Aggregate** | `@Aggregate` | command | yes (event-sourced `AggregateState`) | exactly one `DomainEvent` per command | `@AggregateCommandHandler`, `@EventSourcingHandler` |
| **Service** | `@Service` | command | no | exactly one `ServiceEvent` per command (may also send commands / query) | `@CommandHandler` |
| **Projector** | `@Projector` | query | writes a read model | nothing (void handlers) | `@EventHandler` |
| **Projection** | `@Projection` | query | reads a read model | query results | `@EventHandler`*, `@QueryHandler` |
| **Saga** | `@Saga` | reactive | yes (`SagaState`, persisted) | commands + queries | `@SagaEventHandler` |
| **Observer** | `@Observer` | reactive | no | commands + queries | `@EventHandler` |

\* A `@Projection` may also carry `@EventHandler` methods, but the canonical split in the
reference apps is: **Projector writes** the read model, **Projection reads/queries** it.

What each may / may not do:

- **Invoker** — the *only* place outside reactive components that may originate commands
  and queries. It is the bridge from the outside world (HTTP, CLI) into the bus. It holds
  no domain state and emits no events. Gateways are available only inside its
  `@InvocationHandler` methods (see §3).
- **Aggregate** — the consistency boundary. A command handler validates against the
  current `AggregateState` and returns **one** `DomainEvent`. It must **not** call
  gateways to mutate other aggregates. State is rebuilt by replaying events through
  `@EventSourcingHandler` methods (event sourcing); there is no setter-driven state.
- **Service** — stateless command handling for logic that is not a single
  event-sourced aggregate (integrations, cross-cutting commands). Returns one
  `ServiceEvent`. May itself send commands / queries via injected gateways.
- **Projector** — consumes events and updates a read model (DB table, store). Idempotent,
  retryable, returns `void`. One projector instance is the single active consumer for its
  events (see §3).
- **Projection** — answers queries by reading the read model the projector built.
- **Saga** — a long-running, stateful coordinator. Each `@SagaEventHandler` returns the
  updated `SagaState`; events are correlated to a saga instance by an
  *association property* (see §2). Used to orchestrate multi-step / multi-aggregate
  workflows by reacting to events and sending follow-up commands.
- **Observer** — stateless reaction to events (notifications, side effects). May send
  commands but keeps no state and does no correlation.

### Command side vs query side

- **Command side**: Invoker → `CommandGateway.send(cmd)` → Aggregate/Service command
  handler → produces an event → event is appended to the store and published.
- **Query side**: events are consumed asynchronously by Projectors that build read
  models; Invoker → `QueryGateway.query(q)` → Projection query handler reads the model.

The read side is **eventually consistent**: after a command's future completes, the
event has been stored, but projectors may not yet have applied it.

### Events are the only state-change vehicle

Aggregate/Service command handlers do not mutate state directly; they **return an
event**. Aggregate state changes only by applying events in `@EventSourcingHandler`.
Read models change only inside projector `@EventHandler`s reacting to events. There is no
other sanctioned way to change persistent state.

### One event per command

A command handler (`@AggregateCommandHandler` / `@CommandHandler`) returns a **single**
event object — its return type is one `DomainEvent` (aggregate) or one `ServiceEvent`
(service), never a collection. To model an operation with several effects:

1. **Richer / composite event** — emit one event that carries all the data, and let
   multiple `@EventHandler`s (in one or more projectors/observers) react to it. This is
   the preferred approach.
2. **Saga / Observer delegation** — emit the one event, then have a Saga or Observer
   react to it and `send` additional commands, each of which produces its own event.
   This decomposes a multi-aggregate operation into a chain of single-event commands.

A command handler may also `throw` to reject the command (see §5).

---

## 2. Annotations & handler signatures (verified)

### Component annotations

```java
@Aggregate(snapshotFrequency = 5)   // int snapshotFrequency() default -1; -1 = no snapshots
@Projector(version = 1)             // int version()  — REQUIRED, no default
@Projection                         // no attributes
@Saga(version = 1)                  // int version()  — REQUIRED, no default
@Service                            // no attributes
@Observer(version = 1)              // int version()  — REQUIRED, no default
@Invoker                            // no attributes
```

> The javadoc examples in the source show `context = {...}` on `@Projector`/`@Saga`/
> `@Observer`, but in v2.1.0 those annotations declare **only `version()`** — there is no
> `context` attribute. Per-component contexts are set on the builder instead via
> `Builder.setComponentContexts(Class<?>, String...)`. Do **not** put `context=` in the
> annotation; it will not compile.

All component annotations are `@Target(TYPE) @Retention(RUNTIME)` and meta-annotated
`@Component`.

### Handler annotations

```java
// On @Aggregate methods:
@AggregateCommandHandler(init = false)   // boolean init() default false; init=true => creation handler
@EventSourcingHandler                    // no attributes

// On @Service methods:
@CommandHandler                          // no attributes

// On @Projector / @Projection / @Observer methods:
@EventHandler(retry = -1, retryDelay = 1000)  // int retry() default -1; int retryDelay() default 1000 (ms)

// On @Projection methods:
@QueryHandler                            // no attributes

// On @Saga methods:
@SagaEventHandler(init = false, associationProperty = "orderId", retry = -1, retryDelay = 1000)
//   boolean init() default false; String associationProperty() (REQUIRED); int retry()/retryDelay()

// On @Invoker methods:
@InvocationHandler                       // no attributes
```

All handler annotations are `@Target(METHOD) @Retention(RUNTIME)` and meta-annotated
`@Handler`.

### Handler method parameter injection

Handler methods are invoked by reflection; the framework resolves each parameter **by
type**. You declare only the parameters you need, in any order:

- The **payload** (the command / event / query object) — always present.
- The matching **state** object (`AggregateState` subclass for aggregates, `SagaState`
  subclass for sagas) — for aggregate command/sourcing handlers and saga event handlers.
- `CommandGateway` and/or `QueryGateway` — injected into Service `@CommandHandler`,
  Saga `@SagaEventHandler`, and Observer `@EventHandler` methods that declare them.
  (Inside an Invoker, gateways come from `getCommandGateway()`/`getQueryGateway()`, see §3.)
- `Metadata` — optional.

### Payload base classes & conventions

```java
// Commands (sent via CommandGateway)
abstract class Command extends TrackablePayload {
    public abstract String getAggregateId();   // routing / consistency key
    public abstract String getLockId();
}
abstract class DomainCommand extends Command { // targets an Aggregate
    // getLockId() returns getAggregateId() by default; you implement getAggregateId()
}
abstract class ServiceCommand extends Command { // targets a Service
    public String getLockId()      { return null; }      // override to set a lock key
    public String getAggregateId() { return getLockId(); }
}

// Events (returned by command handlers; never constructed by callers)
abstract class Event extends TrackablePayload {
    public String getContext();
    public <T extends Event> T setContext(String);   // default Context.DEFAULT
}
abstract class DomainEvent  extends Event {}  // returned by @AggregateCommandHandler
abstract class ServiceEvent extends Event {}  // returned by @CommandHandler (Service)

// Queries
abstract class Query<T extends QueryResponse<?>> extends TrackablePayload {
    public Class<T> getResponseType();  // derived from the generic param, do not override
}

// Views & query responses
interface View extends Payload {}                       // your read DTOs implement this
class Single<T extends View>   extends QueryResponse<T>  // Single.of(view)
class Multiple<T extends View> extends QueryResponse<T>  // Multiple.of(collection) / of(varargs)

// State
abstract class AggregateState implements Serializable { boolean isDeleted(); void setDeleted(boolean); }
abstract class SagaState      implements Serializable {
    boolean isEnded(); void setEnded(boolean);
    void setAssociation(String field, String value);
    String getAssociation(String field);
    Map<String,String> getAssociations();
}
```

Conventions:
- **Aggregate id** is whatever `DomainCommand.getAggregateId()` returns — you implement it
  to return your business key (e.g. `return orderId;`). Events are correlated to the same
  aggregate because their handlers read the id from the event fields you set.
- A `DomainCommand` with `init=true` handler **creates** the aggregate; a non-init handler
  requires the aggregate to already exist (else `AggregateNotInitializedError`); an init
  handler on an existing aggregate throws `AggregateInitializedError`.
- Payloads must be JSON-serializable POJOs: **public no-arg constructor + getters/setters**
  for every field. (Jackson is used; missing no-arg ctor or fields => serialization failure.)
- `Query<Single<OrderView>>` ⇒ its `@QueryHandler` must return `Single<OrderView>`.
  `Query<Multiple<OrderView>>` ⇒ returns `Multiple<OrderView>`.

### Gateways

```java
package com.evento.common.messaging.gateway;

interface CommandGateway extends Gateway {
    <R> CompletableFuture<R> send(Command command);
    <R> CompletableFuture<R> send(Command command, long timeout, TimeUnit unit);
    <R> CompletableFuture<R> send(Command command, Metadata metadata);
    // ... overloads with Metadata / handledMessage / timeout
}

interface QueryGateway extends Gateway {
    <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query);
    <T extends QueryResponse<?>> CompletableFuture<T> query(Query<T> query, long timeout, TimeUnit unit);
    // ... overloads with Metadata
}
```

> **There is NO `sendAndWait` in v2.** `send(...)` and `query(...)` are **asynchronous**
> and return `CompletableFuture`. To block, call `.get()` (throws checked
> `ExecutionException` / `InterruptedException`) or `.join()`.
> `send(...)` resolves to the event the handler produced (or, in practice, you often
> ignore the result). `query(...)` resolves to the `QueryResponse` (`Single`/`Multiple`);
> call `.getData()` on it.

---

## 3. Wiring & runtime

### Bootstrap — `EventoBundle.Builder`

The skeleton ships this pre-wired bean (**do not modify it**; mirror it exactly):

```java
package com.study.app.config;

import com.evento.application.EventoBundle;
import com.evento.application.bus.ClusterNodeAddress;
import com.evento.application.bus.EventoServerMessageBusConfiguration;
import com.study.app.App;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class EventoConfiguration {

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
    public EventoBundle eventoApplication(
            @Value("${evento.server.host}") String host,
            @Value("${evento.server.port}") int port,
            @Value("${evento.bundle.id}") String bundleId,
            @Value("${evento.bundle.version}") long version,
            BeanFactory factory) throws Exception {
        return EventoBundle.Builder.builder()
                .setBasePackage(App.class.getPackage())
                .setBundleId(bundleId)
                .setBundleVersion(version)
                .setEventoServerMessageBusConfiguration(new EventoServerMessageBusConfiguration(
                        new ClusterNodeAddress(host, port)))
                .setInjector(factory::getBean)
                .start();
    }
}
```

Key builder methods (all return `Builder`; `start()` returns `EventoBundle`):
`setBasePackage(Package)`, `setBundleId(String)`, `setBundleVersion(long)`,
`setEventoServerMessageBusConfiguration(...)`, `setInjector(Function<Class<?>,Object>)`,
`setComponentContexts(Class<?>, String...)`,
`setConsumerEngineConfigBuilder(BiFunction<EventoServer,PerformanceService,ConsumerEngineConfig>)`,
`setStrictConfinement(boolean)`. Components annotated with the role annotations under the
base package are discovered and registered automatically; `setInjector(factory::getBean)`
means each component is obtained from the Spring context (so you may inject Spring beans
into component constructors).

`@SpringBootApplication public class App` is the base package anchor; components live
under `com.study.app.*`.

### The Invoker pattern (verified)

An Invoker is a class annotated `@Invoker` that **extends `InvokerWrapper`**:

```java
package com.evento.application.proxy;
public class InvokerWrapper {
    protected CommandGateway getCommandGateway(); // valid only inside @InvocationHandler
    protected QueryGateway  getQueryGateway();    // valid only inside @InvocationHandler
}
```

You obtain a usable instance from the bundle with `eventoBundle.getInvoker(MyInvoker.class)`.
That returns a proxy: inside methods annotated `@InvocationHandler`, the protected
`getCommandGateway()` / `getQueryGateway()` accessors return live, telemetry-wrapped
gateways. Called **outside** an `@InvocationHandler` (or on a non-proxied instance) they
throw `RuntimeException("getCommandGateway() called outside an @InvocationHandler scope.")`.

A Spring REST controller bridges HTTP to the bus by **delegating to an Invoker** obtained
from the bundle. The verified, idiomatic shape (see EXAMPLE.md for the full version):

```java
@RestController
public class OrderController {
    private final OrderInvoker invoker;   // an @Invoker extending InvokerWrapper
    OrderController(EventoBundle bundle) {
        this.invoker = bundle.getInvoker(OrderInvoker.class);
    }
    @PostMapping("/orders")
    public String create(@RequestBody CreateOrderRequest r) throws Exception {
        return invoker.createOrder(r.orderId(), r.description(), r.quantity());
    }
}
```

The controller itself holds no gateway; all command/query origination happens inside the
Invoker's `@InvocationHandler` methods, which is what keeps gateway calls *confined* to
components (see §5). Each invocation method blocks on the future (`.get()`) to turn the
async result into an HTTP response.

### Consumer state store / engine options

Consumers (Projectors, Sagas, Observers) run on the v2 consumer engine, configured by
`setConsumerEngineConfigBuilder(...)`. The default (if unset) is **in-memory**:

```java
.setConsumerEngineConfigBuilder(ConsumerEngineConfig::inMemory)  // demos/tests
```

`ConsumerEngineConfig` is a record bundling a `ConsumerProcessor`, a `ConsumerStateStore`
(checkpoint / `isEnabled` probe), and a `DeadEventQueue` (DLQ). `inMemory(...)` wires
in-memory implementations and is for demos/tests only.

**This skeleton is pre-wired for DURABLE persistence** (do not change it): the
`EventoConfiguration` builds the consumer engine from the JDBC consumer state store
(`com.evento.consumer.state.store.jdbc.*`) backed by the provided PostgreSQL —
`JdbcConsumerLock`, `JdbcConsumerStateStore`, `JdbcSagaStateStore`, `JdbcDeadEventQueue`,
`JdbcDedupeStore`. As of v2.1.1 the JDBC consumer state store **auto-creates** the
`evento_v2_*` tables on first connection (default — no manual Flyway step required; own
history table, no clash with your schema).
So consumer checkpoints, saga state, the DLQ, and observer dedup all **survive a
restart**. You never touch this wiring; just build your aggregates/projectors/sagas and
persist any read-model state you add in Postgres too (not in memory).

### Exactly-once / ordering semantics the platform provides

- **Single active consumer per context** — for a given consumer (identified by component
  name + version + context), exactly one instance is active at a time (enforced by the
  consumer *lock*). Bump `version` on a Projector/Saga/Observer to force a replay from the
  start under a new consumer identity.
- **Ordered consumption** — events are delivered to a consumer in event-store sequence
  order; the `ConsumerStateStore` checkpoints progress so a restart resumes where it left.
- **Dedupe** — a dedupe store guards against re-processing the same event (at-least-once
  delivery + dedupe ⇒ effectively-once for idempotent handlers). Make `@EventHandler`s
  idempotent regardless.
- **Retry** — `@EventHandler` / `@SagaEventHandler` support `retry` / `retryDelay`; after
  retries are exhausted the event goes to the **dead-event queue**.

---

## 4. A worked mini-flow

Command → event → projector → projection → query round trip (full code in EXAMPLE.md):

1. HTTP `POST /orders` hits `OrderController`, which calls `OrderInvoker.createOrder(...)`.
2. Inside the `@InvocationHandler`, `getCommandGateway().send(new CreateOrderCommand(id, desc, qty)).get()`.
3. `OrderAggregate.handle(CreateOrderCommand, OrderAggregateState)` (an `init=true`
   `@AggregateCommandHandler`) validates and **returns** `new OrderCreatedEvent(id, desc, qty)`.
4. The framework stores the event; `@EventSourcingHandler on(OrderCreatedEvent, state)`
   rebuilds aggregate state for next time.
5. Asynchronously, `OrderProjector.on(OrderCreatedEvent)` (`@EventHandler`) writes an
   `OrderView` into the read store.
6. HTTP `GET /orders/{id}` → `OrderInvoker.findOrder(id)` →
   `getQueryGateway().query(new FindOrderByIdQuery(id)).get().getData()` →
   `OrderProjection.query(FindOrderByIdQuery)` (`@QueryHandler`) returns
   `Single.of(view)`.

Because step 5 is asynchronous, a `GET` issued immediately after the `POST` future
resolves may not yet see the view (eventual consistency).

---

## 5. Common pitfalls

- **One event per command.** A command handler returns one event object, not a list. Use
  a composite event or saga/observer delegation for multi-effect operations (§1).
- **No `sendAndWait`.** Use `send(...)`/`query(...)` and `.get()`/`.join()`. `.get()`
  throws checked exceptions — declare `throws Exception` on handler/invoker methods.
- **Gateways are confined to components.** Command/Query gateway calls must live inside a
  component class (Invoker `@InvocationHandler`, Service `@CommandHandler`, Saga
  `@SagaEventHandler`, Observer `@EventHandler`). A gateway call in a plain helper /
  injected collaborator / utility is invisible to the framework's static interaction
  analysis. The **`ConfinementScanner`** sweeps non-component classes at startup and
  **logs a warning** for each such call site; with `setStrictConfinement(true)` startup
  **fails** instead. Keep gateway calls in component methods.
- **Aggregate state only via event sourcing.** Never store mutable domain state in
  aggregate fields and never mutate from a command handler. Command handler reads the
  injected `AggregateState` (which may be `null` before the first event — handle it in
  the init handler), decides, returns an event; `@EventSourcingHandler` applies it. The
  state class must extend `AggregateState`; sourcing handlers may return the (possibly
  new) state or mutate it in place and return `void`.
- **Rejections / errors propagate to the sender.** Throwing inside a command handler
  surfaces as a failed `CompletableFuture` at the caller — `.get()` raises
  `ExecutionException` whose cause is your exception (e.g. `IllegalArgumentException`,
  `NoSuchElementException`, or framework errors like `AggregateNotInitializedError`).
  Validate in the handler and throw to reject.
- **Projections lag (eventual consistency).** Do not assume a read model reflects a
  just-sent command. The command future completing means *the event is stored*, not *the
  projector has applied it*.
- **Sagas associate events by property.** Each `@SagaEventHandler` declares
  `associationProperty="<field>"`; the framework reads that field from the incoming event
  to find the saga instance. The `init=true` handler creates the instance and must call
  `state.setAssociation("<field>", value)` so subsequent events correlate. A handler with
  no meaningful correlation (e.g. a standalone init) still needs a non-null
  `associationProperty` string. Return the updated `SagaState`; call `state.setEnded(true)`
  to finish the saga.
- **Virtual threads / async.** Observers (and consumers generally) run on virtual-thread
  executors. Handlers may block on gateway futures (`.get()`); keep them idempotent and
  avoid sharing non-thread-safe mutable state across handler invocations.
- **`@Projector`/`@Saga`/`@Observer` require `version`.** It has no default; omitting it
  is a compile error. There is no `context` attribute on these annotations in v2.1.0.
- **No `@Autowired` on component fields.** The framework rejects field injection at
  startup; inject via the component **constructor** (the injector resolves Spring beans).
  Wrong: `@Autowired private Repo repo;` — Right: `MyProjector(Repo repo) { this.repo = repo; }`.
- **Payload POJOs need no-arg ctor + accessors** for Jackson (de)serialization.
