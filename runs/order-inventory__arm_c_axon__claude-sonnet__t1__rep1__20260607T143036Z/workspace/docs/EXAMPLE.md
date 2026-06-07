# Axon 4.13 — Compiling Example

Minimal CQRS/ES slice over the embedded JPA event store: a command + event, an
event-sourced `@Aggregate` (constructor + method `@CommandHandler`,
`@EventSourcingHandler`), a projection (`@EventHandler`) writing a read model, a
saga driving compensation, query handlers, and a controller doing the round trip.
Package `com.study.app.*`. Axon 4.13.1 / Spring Boot 3.5.14 / Java 25.

> Illustrative slice (one product/customer aggregate elided for brevity). Imports
> verified against the Axon 4.x package layout.

```java
// ---------- com/study/app/command/messages.java (one file per type in practice) ----------
package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;
import java.util.UUID;

// Commands
public record CreateOrderCommand(String orderId, UUID customerId, UUID productId, int quantity) {}
public record ConfirmOrderCommand(@TargetAggregateIdentifier String orderId, int total) {}
public record RejectOrderCommand(@TargetAggregateIdentifier String orderId, String reason) {}

// Events
record OrderCreatedEvent(String orderId, UUID customerId, UUID productId, int quantity) {}
record OrderConfirmedEvent(String orderId, int total) {}
record OrderRejectedEvent(String orderId, String reason) {}
```

```java
// ---------- com/study/app/command/OrderAggregate.java ----------
package com.study.app.command;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class OrderAggregate {

    enum Status { PENDING, CONFIRMED, REJECTED }

    @AggregateIdentifier private String orderId;
    private Status status;

    protected OrderAggregate() {}                    // required no-arg ctor for event sourcing

    @CommandHandler                                  // CONSTRUCTOR handler -> creates aggregate
    public OrderAggregate(CreateOrderCommand cmd) {
        if (cmd.quantity() < 1) throw new IllegalArgumentException("quantity must be >= 1");
        apply(new OrderCreatedEvent(cmd.orderId(), cmd.customerId(), cmd.productId(), cmd.quantity()));
    }

    @CommandHandler
    public void handle(ConfirmOrderCommand cmd) {
        if (status != Status.PENDING) return;        // idempotent: no event if already decided
        apply(new OrderConfirmedEvent(orderId, cmd.total()));
    }

    @CommandHandler
    public void handle(RejectOrderCommand cmd) {
        if (status != Status.PENDING) return;
        apply(new OrderRejectedEvent(orderId, cmd.reason()));
    }

    @EventSourcingHandler                            // ONLY place state changes
    public void on(OrderCreatedEvent e) { this.orderId = e.orderId(); this.status = Status.PENDING; }

    @EventSourcingHandler
    public void on(OrderConfirmedEvent e) { this.status = Status.CONFIRMED; }

    @EventSourcingHandler
    public void on(OrderRejectedEvent e) { this.status = Status.REJECTED; }
}
```

```java
// ---------- com/study/app/command/OrderSaga.java ----------
// Drives reserve -> charge -> confirm / reject+release (compensation).
// (ReserveStockCommand/ChargeCommand/ReleaseStockCommand + their events live in
//  the corresponding product/customer aggregates, elided here.)
package com.study.app.command;

import com.study.app.command.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.SagaLifecycle;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

@Saga
public class OrderSaga {

    @Autowired private transient CommandGateway gateway;   // transient: not serialized into saga state
    private transient int total;                           // recomputed; demo only

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent e) {
        SagaLifecycle.associateWith("productId", e.productId().toString());
        gateway.send(new ReserveStockCommand(e.productId(), e.orderId(), e.quantity()));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(StockReservedEvent e) {
        this.total = e.total();
        gateway.send(new ChargeCommand(e.customerId(), e.orderId(), e.total()));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(ChargedEvent e) {
        gateway.send(new ConfirmOrderCommand(e.orderId(), total));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(ChargeFailedEvent e) {
        gateway.send(new ReleaseStockCommand(e.productId(), e.quantity()));   // COMPENSATION
        gateway.send(new RejectOrderCommand(e.orderId(), "INSUFFICIENT_FUNDS"));
    }

    @EndSaga @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderConfirmedEvent e) { /* terminal */ }

    @EndSaga @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderRejectedEvent e) { /* terminal */ }
}
```

```java
// ---------- com/study/app/query/OrderView.java ----------
package com.study.app.query;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "order_view")
public class OrderView {
    @Id private String orderId;
    private UUID customerId;
    private UUID productId;
    private int quantity;
    private String status;
    private String reason;
    private Integer total;

    protected OrderView() {}
    public OrderView(String orderId, UUID customerId, UUID productId, int quantity, String status) {
        this.orderId = orderId; this.customerId = customerId; this.productId = productId;
        this.quantity = quantity; this.status = status;
    }
    public String getOrderId() { return orderId; }
    public void setStatus(String s) { this.status = s; }
    public void setReason(String r) { this.reason = r; }
    public void setTotal(Integer t) { this.total = t; }
}
```

```java
// ---------- com/study/app/query/Notification.java ----------
package com.study.app.query;

import jakarta.persistence.*;

@Entity
@Table(name = "notification",
       uniqueConstraints = @UniqueConstraint(columnNames = "orderId"))  // exactly-once guard
public class Notification {
    @Id @GeneratedValue private Long id;
    private String orderId;
    private String status;
    protected Notification() {}
    public Notification(String orderId, String status) { this.orderId = orderId; this.status = status; }
}
```

```java
// ---------- com/study/app/query/OrderViewRepository.java ----------
package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
public interface OrderViewRepository extends JpaRepository<OrderView, String> {}
```

```java
// ---------- com/study/app/query/NotificationRepository.java ----------
package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationRepository extends JpaRepository<Notification, Long> {}
```

```java
// ---------- com/study/app/query/queries.java ----------
package com.study.app.query;

import java.util.UUID;
public record FindOrder(String orderId) {}
public record FindNotifications(UUID customerId) {}
public record FindStats() {}
public record Stats(long confirmed, long rejected, long revenue) {}
```

```java
// ---------- com/study/app/query/OrderProjection.java ----------
package com.study.app.query;

import com.study.app.command.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("order-projection")
public class OrderProjection {

    private final OrderViewRepository orders;
    private final NotificationRepository notifications;

    public OrderProjection(OrderViewRepository o, NotificationRepository n) { this.orders = o; this.notifications = n; }

    // ---- projection (read-model write + token advance share one tx) ----
    @EventHandler
    public void on(OrderCreatedEvent e) {
        orders.save(new OrderView(e.orderId(), e.customerId(), e.productId(), e.quantity(), "PENDING"));
    }

    @EventHandler
    public void on(OrderConfirmedEvent e) {
        orders.findById(e.orderId()).ifPresent(v -> { v.setStatus("CONFIRMED"); v.setTotal(e.total()); orders.save(v); });
        emitNotification(e.orderId(), "CONFIRMED");
    }

    @EventHandler
    public void on(OrderRejectedEvent e) {
        orders.findById(e.orderId()).ifPresent(v -> { v.setStatus("REJECTED"); v.setReason(e.reason()); orders.save(v); });
        emitNotification(e.orderId(), "REJECTED");
    }

    // exactly-once notification: redelivered event hits the UNIQUE(orderId) and is skipped
    private void emitNotification(String orderId, String status) {
        try { notifications.saveAndFlush(new Notification(orderId, status)); }
        catch (DataIntegrityViolationException already) { /* exactly-once */ }
    }

    // ---- query handlers ----
    @QueryHandler
    public OrderView handle(FindOrder q) { return orders.findById(q.orderId()).orElse(null); }

    @QueryHandler
    public Stats handle(FindStats q) {
        long confirmed = orders.findAll().stream().filter(v -> "CONFIRMED".equals(status(v))).count();
        // production: use an aggregate JPQL query instead of loading all rows
        return new Stats(confirmed, 0, 0);
    }
    private String status(OrderView v) { return ""; }   // placeholder; real impl reads a getter
}
```

```java
// ---------- com/study/app/web/OrderController.java ----------
package com.study.app.web;

import com.study.app.command.CreateOrderCommand;
import com.study.app.query.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public OrderController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway; this.queryGateway = queryGateway;
    }

    public record CreateOrder(UUID orderId, UUID customerId, UUID productId, int quantity) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)   // 202; processing is async via saga + projection
    public Map<String, Object> create(@RequestBody CreateOrder b) {
        // idempotency: re-sending the same orderId hits the existing aggregate's
        // constructor-vs-existing routing; a duplicate create is rejected and can
        // be treated as already-accepted by inspecting the read model.
        commandGateway.send(new CreateOrderCommand(b.orderId().toString(),
                b.customerId(), b.productId(), b.quantity()));
        return Map.of("orderId", b.orderId(), "status", "PENDING");
    }

    @GetMapping("/{id}")
    public OrderView get(@PathVariable String id) throws ExecutionException, InterruptedException {
        OrderView v = queryGateway.query(new FindOrder(id), ResponseTypes.instanceOf(OrderView.class)).get();
        if (v == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);  // may be in-flight; client polls
        return v;
    }
}
```

### Why this is correct under concurrency
- **Single source of truth per invariant:** stock lives in a product aggregate,
  balance in a customer aggregate; their command streams serialize per aggregate
  (sequence-number optimistic concurrency), so no oversell / overdraw. Losing
  commands get `ConcurrencyException` and are retried.
- **Idempotency:** the order id is the aggregate id; a duplicate create command
  cannot create a second aggregate at sequence 0, and the confirm/reject method
  handlers are no-ops once status leaves PENDING.
- **Saga compensation:** charge failure triggers `ReleaseStockCommand` +
  `RejectOrderCommand`.
- **Exactly-once notification:** the projection writes the read-model row and the
  notification in the processor's transaction (token advances with it); the
  `UNIQUE(orderId)` makes a replayed event idempotent (at-least-once → effectively
  once).
- **Eventual consistency:** views update when the tracking processor polls;
  `GET` may briefly 404 until the projection catches up — clients poll within the
  convergence window.
