# Spring Boot 3.5 — Reference Pack (web + data-jpa)

Terse reference for building a transactional REST backend on Spring Boot 3.5.14
(Java 25, Maven, PostgreSQL) with async processing, idempotency, lock-safe
concurrent writes, and eventually-consistent derived views. Code-heavy.

---

## 1. Core concepts

### 1.1 Stack
- `spring-boot-starter-web` — Spring MVC, embedded Tomcat, Jackson.
- `spring-boot-starter-data-jpa` — Hibernate 6.x (ORM 6), `JpaRepository`,
  `@Transactional`, HikariCP datasource.
- `spring-boot-starter-actuator` — `/actuator/health`, `/actuator/metrics`.
- PostgreSQL JDBC driver (runtime).

### 1.2 Transactional consistency
- A `@Transactional` method runs in one DB transaction; on a thrown
  **unchecked** exception (RuntimeException/Error) it rolls back; on a **checked**
  exception it commits unless you set `rollbackFor`.
- Default isolation = the DB default (PostgreSQL = `READ_COMMITTED`).
- Postgres never blocks readers; concurrent writers to the **same row** block on
  row locks; writers to **different rows** under `READ_COMMITTED` do not see each
  other until commit — hence the need for explicit locking or atomic UPDATEs to
  avoid lost updates.

### 1.3 Optimistic vs pessimistic locking

**Optimistic (`@Version`).** Add a version column; Hibernate appends
`WHERE id=? AND version=?` and `SET version=version+1` to every UPDATE. If the
row changed since you read it, 0 rows match → `OptimisticLockException` /
Spring `ObjectOptimisticLockingFailureException`. No DB locks held; you must
**retry**. Best when contention is low.

```java
@Version
private long version;   // long or @Version Long / int / Integer / Timestamp
```

**Pessimistic (`@Lock` / `SELECT ... FOR UPDATE`).** Acquire a row lock at read
time so concurrent writers block until you commit. Best when contention is high
or the check-then-act window must be serialized (e.g. stock reservation).

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)            // SELECT ... FOR UPDATE
@Query("select p from Product p where p.id = :id")
Optional<Product> findByIdForUpdate(@Param("id") UUID id);
```

`LockModeType` values: `PESSIMISTIC_WRITE` (FOR UPDATE), `PESSIMISTIC_READ`
(FOR SHARE), `OPTIMISTIC`, `OPTIMISTIC_FORCE_INCREMENT`.

### 1.4 Atomic UPDATE for lost-update-free increments
Read-modify-write (`x = read(); x += n; save();`) loses updates under
concurrency. Push the arithmetic into the DB so the row lock is held for the
whole operation:

```java
@Modifying
@Query("update Product p set p.stock = p.stock + :units where p.id = :id")
int addStock(@Param("id") UUID id, @Param("units") int units);
```

`SET stock = stock + ?` is atomic at the row level: concurrent calls serialize on
the row lock and **all** apply. Use the same shape for deposits
(`balance = balance + ?`). For conditional consumption (never negative), guard in
the WHERE clause and check the affected-row count:

```java
@Modifying
@Query("update Product p set p.stock = p.stock - :q where p.id = :id and p.stock >= :q")
int tryReserve(@Param("id") UUID id, @Param("q") int q);  // returns 1 if reserved, 0 if insufficient
```

### 1.5 Unique constraints for idempotency keys
A client-supplied id (order id) is an idempotency key. Make it the PK or a column
with a `UNIQUE` constraint. The DB rejects a duplicate INSERT with a unique
violation, surfaced as `DataIntegrityViolationException`. Catch it and treat as
"already seen" → no second insert, no second side effect. This is race-free even
under concurrent inserts, because uniqueness is enforced by the DB.

### 1.6 Async processing
`@EnableAsync` + `@Async` runs a method on a `TaskExecutor` thread, returning
immediately (`void` or `CompletableFuture<T>`). Lets `POST /orders` return `202`
while processing happens in the background. Define a bounded executor; do not rely
on the default `SimpleAsyncTaskExecutor` (unbounded threads) in production.

```java
@Configuration
@EnableAsync
class AsyncConfig {
  @Bean ThreadPoolTaskExecutor taskExecutor() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(4); ex.setMaxPoolSize(8); ex.setQueueCapacity(1000);
    ex.setThreadNamePrefix("worker-"); ex.initialize();
    return ex;
  }
}
```

### 1.7 `@Transactional` semantics & pitfalls
- **Proxy self-invocation.** `@Transactional`/`@Async` work via a proxy. Calling
  another method **on `this`** bypasses the proxy → the annotation is ignored.
  Call through an injected bean (self-inject or split into another bean).
- `@Transactional` only applies to **public** methods on Spring-managed beans.
- The transaction commits when the **outer** annotated method returns; an
  `@Async` method starts a **new** thread with **no** inherited transaction.
- `propagation = REQUIRES_NEW` suspends the current tx and runs an independent
  one (useful to commit a record even if the caller rolls back).

### 1.8 Retry on serialization / optimistic failures
Optimistic conflicts and serialization failures (`SQLState 40001`) are
**transient**: retry the whole transaction. Use Spring Retry or a manual loop;
each attempt must re-read inside a fresh transaction.

```java
for (int attempt = 0; ; attempt++) {
  try { return self.doInTx(...); }                 // self = proxied bean
  catch (ObjectOptimisticLockingFailureException | CannotAcquireLockException e) {
    if (attempt >= 5) throw e;
  }
}
```

---

## 2. API reference (exact signatures)

### Web (org.springframework.web.bind.annotation)
```java
@RestController
@RequestMapping("/orders")
class OrderController {
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)                       // 202
  OrderView create(@RequestBody @Valid CreateOrder body) { ... }

  @GetMapping("/{id}")
  OrderView get(@PathVariable UUID id) { ... }
}
```
- `@RestController` = `@Controller` + `@ResponseBody`.
- `@RequestBody T` — Jackson-deserialize JSON body to `T`.
- `@PathVariable`, `@RequestParam`, `@Valid` (Jakarta Bean Validation).
- Throw `new ResponseStatusException(HttpStatus.NOT_FOUND, "reason")`
  (`org.springframework.web.server.ResponseStatusException`) for 404/400.
- Map duplicate-key to 200/202 idempotent response, not 500.

### Data JPA (org.springframework.data.jpa.repository)
```java
public interface ProductRepository extends JpaRepository<Product, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Product p where p.id = :id")
  Optional<Product> findByIdForUpdate(@Param("id") UUID id);

  @Modifying
  @Query("update Product p set p.stock = p.stock + :u where p.id = :id")
  int addStock(@Param("id") UUID id, @Param("u") int u);
}
```
- `JpaRepository<T,ID>`: `save`, `saveAndFlush`, `findById`, `getReferenceById`,
  `findAll`, `deleteById`, `count`, `existsById`.
- `@Modifying` (required for UPDATE/DELETE JPQL); returns `int` affected rows.
  Add `@Modifying(clearAutomatically = true, flushAutomatically = true)` if you
  read the same entity again in the same tx.
- `@Lock` from `jakarta.persistence.LockModeType`.
- `@Transactional(isolation = Isolation.SERIALIZABLE)` /
  `Isolation.REPEATABLE_READ` / `READ_COMMITTED`
  (`org.springframework.transaction.annotation.{Transactional, Isolation, Propagation}`).

### Events (transactional, exactly-once-ish dispatch)
```java
// org.springframework.context.ApplicationEventPublisher
publisher.publishEvent(new OrderDecided(orderId, status));

// org.springframework.transaction.event.{TransactionalEventListener, TransactionPhase}
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
void onDecided(OrderDecided e) { ... }   // fires only if the tx committed
```
- Default phase is `AFTER_COMMIT`; other phases: `BEFORE_COMMIT`,
  `AFTER_ROLLBACK`, `AFTER_COMPLETION`.
- `AFTER_COMMIT` guarantees the listener never sees an event whose tx rolled
  back. To also **persist** in the listener, annotate it
  `@Transactional(propagation = REQUIRES_NEW)` (the original tx is already
  committed/closed).
- For true exactly-once side effects, gate on a committed DB state transition
  (see §4) rather than relying on the event alone.

### Exception handling
```java
// org.springframework.dao.DataIntegrityViolationException  (wraps unique violations)
try { repo.save(order); }
catch (DataIntegrityViolationException dup) { /* idempotent: already exists */ }

@RestControllerAdvice
class Errors {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> rse(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("error", e.getReason()));
  }
}
```
- `MethodArgumentNotValidException` / `HttpMessageNotReadableException` → 400
  (handled by Spring Boot's default error mapping; override via `@ExceptionHandler`).

---

## 3. Wiring (application.properties)

```properties
server.port=${PORT:8080}
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/app}
spring.datasource.username=${DB_USER:postgres}
spring.datasource.password=${DB_PASS:secret}

# Hibernate schema generation (skeleton uses 'update'; never use in real prod)
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

# HikariCP (pool sizing must exceed async worker count to avoid starvation)
spring.datasource.hikari.maximum-pool-size=15
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=10000

# Actuator
management.endpoints.web.exposure.include=health,metrics
management.endpoint.health.show-details=never
```
- DDL `update` adds tables/columns from `@Entity` mappings; it does NOT add
  arbitrary `UNIQUE`/`CHECK` constraints unless declared via `@Table`/`@Column`.
  Declare idempotency uniqueness in the entity (see §4) so it is created.
- Pool sizing: a request thread + the async worker may each need a connection;
  size Hikari so worker pool + expected concurrent web requests fit.

---

## 4. Worked mini-flow

Pattern: `POST /orders` inserts a PENDING row (idempotent via PK), returns 202,
and dispatches async work. The worker does the saga (reserve → charge →
confirm/reject+release) and is **exactly-once** because every side effect is
gated by a committed status transition `PENDING → CONFIRMED|REJECTED` guarded in
the WHERE clause: only the first thread to flip it from PENDING proceeds.

### Entity with @Version (optimistic) + atomic counters
```java
@Entity @Table(name = "product")
class Product {
  @Id UUID id;
  String name;
  int unitPrice;
  int stock;
  @Version long version;
}
```

### Repository: atomic, conditional, lost-update-free
```java
public interface ProductRepository extends JpaRepository<Product, UUID> {
  @Modifying
  @Query("update Product p set p.stock = p.stock + :u where p.id = :id")
  int restock(@Param("id") UUID id, @Param("u") int u);

  @Modifying
  @Query("update Product p set p.stock = p.stock - :q where p.id = :id and p.stock >= :q")
  int reserve(@Param("id") UUID id, @Param("q") int q);   // 1 = ok, 0 = OUT_OF_STOCK
}
```

### Service: idempotent create + safe check-then-act
```java
@Service
class OrderService {
  private final OrderRepository orders; private final ProductRepository products;
  private final CustomerRepository customers; private final OrderService self; // proxied self
  // ... constructor; self injected via @Lazy to call @Async/@Transactional through the proxy

  @Transactional
  public OrderView create(CreateOrder cmd) {
    if (orders.existsById(cmd.orderId())) return view(orders.findById(cmd.orderId()).get());
    try {
      orders.saveAndFlush(new Order(cmd.orderId(), cmd.customerId(),
                                    cmd.productId(), cmd.quantity(), Status.PENDING));
    } catch (DataIntegrityViolationException dup) {           // concurrent duplicate
      return view(orders.findById(cmd.orderId()).orElseThrow());
    }
    self.processAsync(cmd.orderId());                          // through proxy → real @Async
    return new OrderView(cmd.orderId(), Status.PENDING);
  }

  @Async
  public void processAsync(UUID orderId) { self.process(orderId); }   // tx starts in process()

  @Transactional
  public void process(UUID orderId) {
    Order o = orders.findById(orderId).orElseThrow();
    if (o.status != Status.PENDING) return;                   // already decided → no-op (idempotent)
    int total = o.quantity * products.findById(o.productId).orElseThrow().unitPrice;

    if (products.reserve(o.productId, o.quantity) == 0) { decide(o, Status.REJECTED, "OUT_OF_STOCK"); return; }
    if (customers.charge(o.customerId, total) == 0) {         // charge = balance-:t where balance>=:t
      products.restock(o.productId, o.quantity);              // COMPENSATION: release reservation
      decide(o, Status.REJECTED, "INSUFFICIENT_FUNDS"); return;
    }
    decide(o, Status.CONFIRMED, null);                        // sets total too
  }

  private void decide(Order o, Status s, String reason) {
    o.status = s; o.reason = reason;                          // optimistic @Version protects against double-decide
    orders.save(o);
    publisher.publishEvent(new OrderDecided(o.id, s));        // notification fired AFTER_COMMIT
  }
}
```

### Exactly-once notification via AFTER_COMMIT + committed-state guard
The `process` method is a no-op when status is not PENDING, so even if `@Async`
retries or runs twice, only the first committed transition emits a decision. The
`@TransactionalEventListener(AFTER_COMMIT)` then writes exactly one notification
row. Make the notification table carry a `UNIQUE(orderId)` so a re-delivered
event is deduplicated by the DB:

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
void notify(OrderDecided e) {
  try { notifications.saveAndFlush(new Notification(e.orderId(), e.status())); }
  catch (DataIntegrityViolationException already) { /* exactly-once: skip */ }
}
```

### Eventually-consistent stats view
Stats (`confirmed`, `rejected`, `revenue`) are **derived**. Either compute on
read with an aggregate query, or maintain a counters row updated atomically in
the same committed transition. Reads may lag writes by up to the convergence
window; that is acceptable for an eventually-consistent view.

```java
@Query("select new com.study.app.query.Stats(" +
       " sum(case when o.status='CONFIRMED' then 1 else 0 end)," +
       " sum(case when o.status='REJECTED'  then 1 else 0 end)," +
       " coalesce(sum(case when o.status='CONFIRMED' then o.total else 0 end),0)) from Order o")
Stats stats();
```

---

## 5. Common pitfalls

1. **Lost updates from read-modify-write.** `e = find(); e.setStock(e.getStock()+n); save();`
   loses concurrent increments. Use atomic `UPDATE ... SET x = x + ?` (§1.4) or
   pessimistic lock the row first.
2. **Overselling / negative balance.** Guard consumption in the WHERE clause
   (`... where stock >= :q`) and branch on the affected-row count; do not read
   then subtract.
3. **Double-processing without an idempotency key.** Without a UNIQUE on the
   client order id, a retried `POST /orders` creates a second order and charges
   twice. Use the order id as PK / UNIQUE and catch
   `DataIntegrityViolationException`.
4. **Proxy self-invocation.** `this.process(...)` from within the same bean
   ignores `@Async`/`@Transactional`. Inject the proxied self (`@Lazy`) or split
   into a separate bean.
5. **`@Async` + `@Transactional` interplay.** The async method runs on a new
   thread with **no** ambient transaction; start the transaction *inside* the
   async method (or in a method it calls through the proxy). An entity loaded in
   the caller's tx is detached in the worker — re-load by id.
6. **Listener can't see the row it reacts to.** With `AFTER_COMMIT`, the
   committing tx is closed; a `@TransactionalEventListener` that writes must use
   `REQUIRES_NEW`, and should re-load entities by id.
7. **Eventual consistency of derived views.** Don't assert read-your-writes on
   stats/notifications immediately; allow the convergence window and converge via
   committed transitions, not in-memory state.
8. **Connection-pool starvation.** Async workers + web threads competing for a
   too-small Hikari pool deadlock. Size the pool above worker count.
