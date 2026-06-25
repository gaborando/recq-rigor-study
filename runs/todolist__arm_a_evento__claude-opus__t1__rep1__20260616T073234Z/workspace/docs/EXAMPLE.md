# Evento Framework — Complete Minimal Example (v2.1.0)

A single-bundle order app under `com.study.app.*`, adapted from the `evento-lab`
reference. It demonstrates: one **aggregate** (two commands/events), one **projector** +
**projection** + **query**, one **saga** reacting to an event and sending a command, one
**observer**, and a **REST controller invoker** calling the gateways.

This is the imitation template — every import is shown and the code compiles against
`evento-bundle:2.1.0` + Spring Boot 3.5.x. The `App` and `EventoConfiguration` classes are
the pre-wired skeleton (shown for completeness; do not modify them).

Package layout (matches the mandated layers: `web`, `command`, `query`, `domain`, `config`):
```
com.study.app
├── App.java                         (@SpringBootApplication)
├── config/EventoConfiguration.java  (pre-wired bundle bean)
├── domain/command/  CreateOrderCommand, ConfirmOrderCommand     (payload types)
├── domain/event/    OrderCreatedEvent, OrderConfirmedEvent
├── domain/query/    FindOrderByIdQuery, ListOrdersQuery
├── domain/view/     OrderView
├── command/         OrderAggregate, OrderAggregateState, OrderService,
│                    OrderSaga, OrderSagaState, OrderObserver    (write/process side)
├── query/           OrderProjector, OrderProjection, OrderViewStore (read side)
└── web/             OrderInvoker, OrderController               (HTTP boundary)
```

---

## domain/command — commands

```java
package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrderCommand extends DomainCommand {
    private String orderId;
    private String description;
    private int quantity;

    public CreateOrderCommand() {}
    public CreateOrderCommand(String orderId, String description, int quantity) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() { return orderId; }   // aggregate routing key

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
```

```java
package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.ServiceCommand;

public class ConfirmOrderCommand extends ServiceCommand {
    private String orderId;

    public ConfirmOrderCommand() {}
    public ConfirmOrderCommand(String orderId) { this.orderId = orderId; }

    @Override
    public String getLockId() { return "confirm-" + orderId; }  // optional lock key

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
```

## domain/event — events

```java
package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderCreatedEvent extends DomainEvent {
    private String orderId;
    private String description;
    private int quantity;

    public OrderCreatedEvent() {}
    public OrderCreatedEvent(String orderId, String description, int quantity) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
```

```java
package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.ServiceEvent;

public class OrderConfirmedEvent extends ServiceEvent {
    private String orderId;

    public OrderConfirmedEvent() {}
    public OrderConfirmedEvent(String orderId) { this.orderId = orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
```

## domain/view — read DTO

```java
package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class OrderView implements View {
    private String orderId;
    private String description;
    private int quantity;
    private String status;

    public OrderView() {}
    public OrderView(String orderId, String description, int quantity, String status) {
        this.orderId = orderId;
        this.description = description;
        this.quantity = quantity;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

## domain/query — queries

```java
package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.OrderView;

public class FindOrderByIdQuery extends Query<Single<OrderView>> {
    private String orderId;

    public FindOrderByIdQuery() {}
    public FindOrderByIdQuery(String orderId) { this.orderId = orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
```

```java
package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.view.OrderView;

public class ListOrdersQuery extends Query<Multiple<OrderView>> {
    public ListOrdersQuery() {}
}
```

---

## command — aggregate, state, service

```java
package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

public class OrderAggregateState extends AggregateState {
    private String description;
    private int quantity;
    private String status;

    public OrderAggregateState() {}

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

```java
package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.CreateOrderCommand;
import com.study.app.domain.event.OrderCreatedEvent;

@Aggregate(snapshotFrequency = 5)
public class OrderAggregate {

    // init=true => creation handler; state is null until the first event is applied.
    @AggregateCommandHandler(init = true)
    OrderCreatedEvent handle(CreateOrderCommand cmd, OrderAggregateState state) {
        if (cmd.getOrderId() == null || cmd.getOrderId().isBlank()) {
            throw new IllegalArgumentException("orderId is required"); // rejects the command
        }
        return new OrderCreatedEvent(cmd.getOrderId(), cmd.getDescription(), cmd.getQuantity());
    }

    // Returns the (possibly newly created) state; the framework persists it for replay.
    @EventSourcingHandler
    OrderAggregateState on(OrderCreatedEvent e, OrderAggregateState state) {
        if (state == null) state = new OrderAggregateState();
        state.setDescription(e.getDescription());
        state.setQuantity(e.getQuantity());
        state.setStatus("CREATED");
        return state;
    }
}
```

The Service handles the `ConfirmOrderCommand` (stateless command logic) and emits a
`ServiceEvent`:

```java
package com.study.app.command;

import com.evento.common.modeling.annotations.component.Service;
import com.evento.common.modeling.annotations.handler.CommandHandler;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.event.OrderConfirmedEvent;

@Service
public class OrderService {

    @CommandHandler
    OrderConfirmedEvent handle(ConfirmOrderCommand cmd) {
        return new OrderConfirmedEvent(cmd.getOrderId());
    }
}
```

---

## query — read store, projector, projection

```java
package com.study.app.query;

import com.study.app.domain.view.OrderView;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Minimal in-memory read model. In a real app this would be JPA/JDBC. */
public final class OrderViewStore {
    private static final ConcurrentMap<String, OrderView> VIEWS = new ConcurrentHashMap<>();
    private OrderViewStore() {}
    public static void put(OrderView v) { VIEWS.put(v.getOrderId(), v); }
    public static OrderView get(String orderId) { return VIEWS.get(orderId); }
    public static Collection<OrderView> getAll() { return VIEWS.values(); }
}
```

```java
package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderCreatedEvent;
import com.study.app.domain.view.OrderView;

@Projector(version = 1)   // version is REQUIRED; bump it to force a replay
public class OrderProjector {

    @EventHandler
    void on(OrderCreatedEvent e) {                 // void; idempotent
        OrderViewStore.put(new OrderView(e.getOrderId(), e.getDescription(), e.getQuantity(), "CREATED"));
    }

    @EventHandler(retry = 3)                        // retry up to 3 times before DLQ
    void on(OrderConfirmedEvent e) {
        var v = OrderViewStore.get(e.getOrderId());
        if (v != null) v.setStatus("CONFIRMED");
    }
}
```

```java
package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.FindOrderByIdQuery;
import com.study.app.domain.query.ListOrdersQuery;
import com.study.app.domain.view.OrderView;

import java.util.NoSuchElementException;

@Projection
public class OrderProjection {

    @QueryHandler
    Single<OrderView> query(FindOrderByIdQuery q) {
        var v = OrderViewStore.get(q.getOrderId());
        if (v == null) throw new NoSuchElementException("order not found: " + q.getOrderId());
        return Single.of(v);
    }

    @QueryHandler
    Multiple<OrderView> query(ListOrdersQuery q) {
        return Multiple.of(OrderViewStore.getAll());
    }
}
```

---

## command — saga, saga state, observer (process side)

The saga reacts to `OrderCreatedEvent` and, after correlating by `orderId`, **sends** a
`ConfirmOrderCommand` (here: auto-confirm orders of quantity > 0). It declares the
`CommandGateway` as a handler parameter.

```java
package com.study.app.command;

import com.evento.common.modeling.state.SagaState;

public class OrderSagaState extends SagaState {
    private String orderId;
    private String phase;

    public OrderSagaState() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
}
```

```java
package com.study.app.command;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderCreatedEvent;

@Saga(version = 1)
public class OrderSaga {

    // init handler: creates the saga instance and records the association.
    @SagaEventHandler(init = true, associationProperty = "orderId")
    OrderSagaState on(OrderCreatedEvent e, CommandGateway cg) throws Exception {
        var state = new OrderSagaState();
        state.setAssociation("orderId", e.getOrderId());   // correlate future events
        state.setOrderId(e.getOrderId());
        state.setPhase("CREATED");
        if (e.getQuantity() > 0) {
            cg.send(new ConfirmOrderCommand(e.getOrderId())).get();  // async; block on result
            state.setPhase("CONFIRMING");
        }
        return state;
    }

    // subsequent event correlated by orderId; ends the saga.
    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(OrderConfirmedEvent e, OrderSagaState state) {
        state.setPhase("CONFIRMED");
        state.setEnded(true);   // saga lifecycle complete
        return state;
    }
}
```

The observer is a stateless side-effect reactor:

```java
package com.study.app.command;

import com.evento.common.modeling.annotations.component.Observer;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderCreatedEvent;

@Observer(version = 1)
public class OrderObserver {

    @EventHandler
    void on(OrderCreatedEvent e) {
        System.out.println("[observer] order created: " + e.getOrderId());
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        System.out.println("[observer] order confirmed: " + e.getOrderId());
    }
}
```

---

## web — Invoker component + REST controller bridge

The Invoker extends `InvokerWrapper`; its `@InvocationHandler` methods are the only place
gateways originate commands/queries. `getCommandGateway()` / `getQueryGateway()` are live
only inside these methods on the proxy returned by `EventoBundle.getInvoker(...)`.

```java
package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.study.app.domain.command.CreateOrderCommand;
import com.study.app.domain.query.FindOrderByIdQuery;
import com.study.app.domain.query.ListOrdersQuery;
import com.study.app.domain.view.OrderView;

import java.util.Collection;

@Invoker
public class OrderInvoker extends InvokerWrapper {

    @InvocationHandler
    public String createOrder(String orderId, String description, int quantity) throws Exception {
        getCommandGateway().send(new CreateOrderCommand(orderId, description, quantity)).get();
        return orderId;
    }

    @InvocationHandler
    public OrderView findOrder(String orderId) throws Exception {
        // query(...) resolves to Single<OrderView>; .getData() unwraps the view
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Single<OrderView>>query(new FindOrderByIdQuery(orderId))
                .get()
                .getData();
    }

    @InvocationHandler
    public Collection<OrderView> listOrders() throws Exception {
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Multiple<OrderView>>query(new ListOrdersQuery())
                .get()
                .getData();
    }
}
```

```java
package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.OrderView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
public class OrderController {

    private final OrderInvoker invoker;

    // Obtain the Invoker proxy from the bundle; the controller holds no gateway itself.
    public OrderController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(OrderInvoker.class);
    }

    public record CreateOrderRequest(String orderId, String description, int quantity) {}

    @PostMapping("/orders")
    public String create(@RequestBody CreateOrderRequest r) throws Exception {
        return invoker.createOrder(r.orderId(), r.description(), r.quantity());
    }

    @GetMapping("/orders/{orderId}")
    public OrderView find(@PathVariable String orderId) throws Exception {
        return invoker.findOrder(orderId);   // may 500 (NoSuchElementException) until projector catches up
    }

    @GetMapping("/orders")
    public Collection<OrderView> list() throws Exception {
        return invoker.listOrders();
    }
}
```

---

## App + config (pre-wired skeleton — shown for completeness)

```java
package com.study.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

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

### End-to-end behavior

`POST /orders {"orderId":"o1","description":"Widget","quantity":2}`:
1. `OrderInvoker.createOrder` → `CommandGateway.send(CreateOrderCommand)` →
   `OrderAggregate.handle(...)` returns `OrderCreatedEvent`.
2. `OrderProjector` writes the `OrderView` (status `CREATED`); `OrderObserver` logs it;
   `OrderSaga` (init) sends `ConfirmOrderCommand`.
3. `OrderService.handle(ConfirmOrderCommand)` returns `OrderConfirmedEvent` →
   `OrderProjector` sets status `CONFIRMED`; `OrderSaga` second handler ends the saga.
4. `GET /orders/o1` → `FindOrderByIdQuery` → `OrderProjection` returns the view
   (eventually `CONFIRMED`). Issued too early, it may 500 until projectors catch up.
