# Spring Boot — Compiling Example

Minimal end-to-end slice: a JPA entity with `@Version`, a repository with an
atomic increment + conditional consume, a service doing **safe** check-then-act
with locking and an idempotency key (unique constraint + duplicate catch), and an
`@Async` processor whose side effects are exactly-once via committed state
transitions. Package `com.study.app.*`. Targets Spring Boot 3.5.14 / Java 25.

> Illustrative slice, not the full spec. Shows the load-bearing patterns.

```java
// ---------- com/study/app/domain/Product.java ----------
package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "product")
public class Product {
    @Id private UUID id;
    private String name;
    private int unitPrice;          // cents
    private int stock;
    @Version private long version;  // optimistic lock

    protected Product() {}
    public Product(UUID id, String name, int unitPrice, int stock) {
        this.id = id; this.name = name; this.unitPrice = unitPrice; this.stock = stock;
    }
    public UUID getId() { return id; }
    public int getUnitPrice() { return unitPrice; }
    public int getStock() { return stock; }
}
```

```java
// ---------- com/study/app/domain/Order.java ----------
package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "orders")     // client-supplied id is the PK => idempotency key
public class Order {
    public enum Status { PENDING, CONFIRMED, REJECTED }

    @Id private UUID id;            // client-supplied order id
    private UUID customerId;
    private UUID productId;
    private int quantity;
    @Enumerated(EnumType.STRING) private Status status;
    private String reason;
    private Integer total;
    @Version private long version;

    protected Order() {}
    public Order(UUID id, UUID customerId, UUID productId, int quantity) {
        this.id = id; this.customerId = customerId; this.productId = productId;
        this.quantity = quantity; this.status = Status.PENDING;
    }
    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public void decide(Status s, String reason, Integer total) {
        this.status = s; this.reason = reason; this.total = total;
    }
}
```

```java
// ---------- com/study/app/domain/Notification.java ----------
package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "notification",
       uniqueConstraints = @UniqueConstraint(columnNames = "orderId"))  // exactly-once
public class Notification {
    @Id @GeneratedValue private Long id;
    private UUID orderId;
    private UUID customerId;
    @Enumerated(EnumType.STRING) private Order.Status status;

    protected Notification() {}
    public Notification(UUID orderId, UUID customerId, Order.Status status) {
        this.orderId = orderId; this.customerId = customerId; this.status = status;
    }
}
```

```java
// ---------- com/study/app/command/ProductRepository.java ----------
package com.study.app.command;

import com.study.app.domain.Product;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    // Atomic, lost-update-free increment: all concurrent restocks apply.
    @Modifying
    @Query("update Product p set p.stock = p.stock + :u where p.id = :id")
    int restock(@Param("id") UUID id, @Param("u") int u);

    // Conditional consume: never oversell. Returns 1 if reserved, 0 if insufficient.
    @Modifying
    @Query("update Product p set p.stock = p.stock - :q where p.id = :id and p.stock >= :q")
    int reserve(@Param("id") UUID id, @Param("q") int q);
}
```

```java
// ---------- com/study/app/command/CustomerRepository.java ----------
package com.study.app.command;

import com.study.app.domain.Customer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    @Modifying
    @Query("update Customer c set c.balance = c.balance + :a where c.id = :id")
    int deposit(@Param("id") UUID id, @Param("a") int a);

    @Modifying
    @Query("update Customer c set c.balance = c.balance - :t where c.id = :id and c.balance >= :t")
    int charge(@Param("id") UUID id, @Param("t") int t);   // 1 = charged, 0 = INSUFFICIENT_FUNDS
}
```

```java
// ---------- com/study/app/command/OrderRepository.java ----------
package com.study.app.command;

import com.study.app.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {}
```

```java
// ---------- com/study/app/command/NotificationRepository.java ----------
package com.study.app.command;

import com.study.app.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {}
```

```java
// ---------- com/study/app/command/OrderDecided.java ----------
package com.study.app.command;

import com.study.app.domain.Order;
import java.util.UUID;

public record OrderDecided(UUID orderId, UUID customerId, Order.Status status) {}
```

```java
// ---------- com/study/app/config/AsyncConfig.java ----------
package com.study.app.config;

import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        var ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4); ex.setMaxPoolSize(8); ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("worker-"); ex.initialize();
        return ex;
    }
}
```

```java
// ---------- com/study/app/command/OrderService.java ----------
package com.study.app.command;

import com.study.app.domain.*;
import com.study.app.domain.Order.Status;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.event.*;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orders;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final NotificationRepository notifications;
    private final ApplicationEventPublisher publisher;
    private final OrderService self;   // proxied self → @Async/@Transactional honored

    public OrderService(OrderRepository orders, ProductRepository products,
                        CustomerRepository customers, NotificationRepository notifications,
                        ApplicationEventPublisher publisher, @Lazy OrderService self) {
        this.orders = orders; this.products = products; this.customers = customers;
        this.notifications = notifications; this.publisher = publisher; this.self = self;
    }

    /** Idempotent on the client order id. Returns immediately; processing is async. */
    @Transactional
    public Status create(UUID orderId, UUID customerId, UUID productId, int quantity) {
        var existing = orders.findById(orderId);
        if (existing.isPresent()) return existing.get().getStatus();   // idempotent replay
        try {
            orders.saveAndFlush(new Order(orderId, customerId, productId, quantity));
        } catch (DataIntegrityViolationException duplicate) {          // concurrent first-create
            return orders.findById(orderId).orElseThrow().getStatus();
        }
        self.processAsync(orderId);
        return Status.PENDING;
    }

    @Async
    public void processAsync(UUID orderId) {
        try { self.process(orderId); } catch (RuntimeException retryable) { self.process(orderId); }
    }

    /** Saga: reserve stock -> charge -> confirm / reject+release. Exactly-once via state guard. */
    @Transactional
    public void process(UUID orderId) {
        Order o = orders.findById(orderId).orElseThrow();
        if (o.getStatus() != Status.PENDING) return;                  // already decided => no-op

        Product p = products.findById(o.getProductId()).orElseThrow();
        int total = o.getQuantity() * p.getUnitPrice();

        if (products.reserve(o.getProductId(), o.getQuantity()) == 0) {
            decide(o, Status.REJECTED, "OUT_OF_STOCK", null); return;
        }
        if (customers.charge(o.getCustomerId(), total) == 0) {
            products.restock(o.getProductId(), o.getQuantity());      // COMPENSATION: release
            decide(o, Status.REJECTED, "INSUFFICIENT_FUNDS", null); return;
        }
        decide(o, Status.CONFIRMED, null, total);
    }

    private void decide(Order o, Status s, String reason, Integer total) {
        o.decide(s, reason, total);
        orders.save(o);                                               // @Version blocks double-decide
        publisher.publishEvent(new OrderDecided(o.getId(), o.getCustomerId(), s));
    }

    /** Exactly-once notification: only fires after commit; UNIQUE(orderId) dedupes redelivery. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDecided(OrderDecided e) {
        try {
            notifications.saveAndFlush(new Notification(e.orderId(), e.customerId(), e.status()));
        } catch (DataIntegrityViolationException already) { /* exactly-once: skip */ }
    }
}
```

```java
// ---------- com/study/app/web/OrderController.java ----------
package com.study.app.web;

import com.study.app.command.OrderService;
import com.study.app.domain.Order.Status;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service = service; }

    public record CreateOrder(UUID orderId, UUID customerId, UUID productId, int quantity) {}

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)   // 202
    public Map<String, Object> create(@RequestBody CreateOrder b) {
        Status status = service.create(b.orderId(), b.customerId(), b.productId(), b.quantity());
        return Map.of("orderId", b.orderId(), "status", status);
    }
}
```

### Why this is correct under concurrency
- **Idempotency:** order id is the PK; concurrent first-creates race on the
  INSERT and the loser catches `DataIntegrityViolationException`.
- **No lost updates:** restock/deposit are atomic `x = x + ?`; reserve/charge are
  conditional `x = x - ?  WHERE x >= ?`, so they serialize on the row lock and
  can never oversell or overdraw.
- **Saga + compensation:** charge failure releases the reservation via `restock`.
- **Exactly-once decision:** `process` is a no-op unless status is still PENDING;
  `@Version` guards the transition; the notification listener only runs after a
  successful commit and dedupes on `UNIQUE(orderId)`.
