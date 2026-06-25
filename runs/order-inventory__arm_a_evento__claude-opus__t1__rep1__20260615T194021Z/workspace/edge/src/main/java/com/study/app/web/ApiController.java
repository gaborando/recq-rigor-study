package com.study.app.web;

import com.evento.application.EventoBundle;
import com.evento.common.modeling.exceptions.AggregateInitializedError;
import com.evento.common.modeling.exceptions.AggregateNotInitializedError;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;
import com.study.app.domain.view.OrderView;
import com.study.app.domain.view.ProductView;
import com.study.app.domain.view.StatsView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
public class ApiController {

    private final ApiInvoker invoker;

    public ApiController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(ApiInvoker.class);
    }

    // ---------------- request DTOs ----------------

    public record CreateProductRequest(String name, Long unitPrice, Long stock) {}
    public record RestockRequest(Long units) {}
    public record CreateCustomerRequest(String name, Long balance) {}
    public record DepositRequest(Long amount) {}
    public record CreateOrderRequest(String orderId, String customerId, String productId, Integer quantity) {}

    // ---------------- products ----------------

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody CreateProductRequest r) throws Exception {
        if (r == null || r.name() == null || r.name().isBlank()
                || r.unitPrice() == null || r.unitPrice() < 1
                || r.stock() == null || r.stock() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed product");
        }
        String id = UUID.randomUUID().toString();
        invoker.createProduct(id, r.name(), r.unitPrice(), r.stock());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", r.name());
        body.put("unitPrice", r.unitPrice());
        body.put("stock", r.stock());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/products/{id}")
    public ProductView getProduct(@PathVariable String id) throws Exception {
        try {
            return invoker.getProduct(id);
        } catch (Exception e) {
            throw notFoundIfMissing(e, "product");
        }
    }

    @PostMapping("/products/{id}/restock")
    public ResponseEntity<?> restock(@PathVariable String id, @RequestBody RestockRequest r) throws Exception {
        if (r == null || r.units() == null || r.units() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "units must be >= 1");
        }
        try {
            invoker.restock(id, r.units());
        } catch (Exception e) {
            throw notFoundIfMissing(e, "product");
        }
        return ResponseEntity.accepted().build();
    }

    // ---------------- customers ----------------

    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(@RequestBody CreateCustomerRequest r) throws Exception {
        if (r == null || r.name() == null || r.name().isBlank()
                || r.balance() == null || r.balance() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed customer");
        }
        String id = UUID.randomUUID().toString();
        invoker.createCustomer(id, r.name(), r.balance());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", r.name());
        body.put("balance", r.balance());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping("/customers/{id}")
    public CustomerView getCustomer(@PathVariable String id) throws Exception {
        try {
            return invoker.getCustomer(id);
        } catch (Exception e) {
            throw notFoundIfMissing(e, "customer");
        }
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<?> deposit(@PathVariable String id, @RequestBody DepositRequest r) throws Exception {
        if (r == null || r.amount() == null || r.amount() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be >= 1");
        }
        try {
            invoker.deposit(id, r.amount());
        } catch (Exception e) {
            throw notFoundIfMissing(e, "customer");
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/customers/{id}/notifications")
    public Collection<NotificationView> notifications(@PathVariable String id) throws Exception {
        return invoker.getNotifications(id);
    }

    // ---------------- orders ----------------

    @PostMapping("/orders")
    public ResponseEntity<?> createOrder(@RequestBody CreateOrderRequest r) throws Exception {
        if (r == null || r.orderId() == null || r.orderId().isBlank()
                || r.customerId() == null || r.customerId().isBlank()
                || r.productId() == null || r.productId().isBlank()
                || r.quantity() == null || r.quantity() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "malformed order");
        }
        try {
            invoker.createOrder(r.orderId(), r.customerId(), r.productId(), r.quantity());
        } catch (Exception e) {
            // Idempotent replay: the same orderId was already created. Not an error.
            if (!causedBy(e, AggregateInitializedError.class)) {
                if (causedBy(e, IllegalArgumentException.class)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid order");
                }
                throw e;
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", r.orderId());
        body.put("customerId", r.customerId());
        body.put("productId", r.productId());
        body.put("quantity", r.quantity());
        body.put("status", "PENDING");
        return ResponseEntity.accepted().body(body);
    }

    @GetMapping("/orders/{id}")
    public OrderView getOrder(@PathVariable String id) throws Exception {
        try {
            return invoker.getOrder(id);
        } catch (Exception e) {
            throw notFoundIfMissing(e, "order");
        }
    }

    // ---------------- stats ----------------

    @GetMapping("/stats/orders")
    public StatsView stats() throws Exception {
        return invoker.getStats();
    }

    // ---------------- helpers ----------------

    private ResponseStatusException notFoundIfMissing(Exception e, String what) {
        if (causedBy(e, NoSuchElementException.class) || causedBy(e, AggregateNotInitializedError.class)) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = (t.getCause() == t ? null : t.getCause())) {
            if (type.isInstance(t)) return true;
        }
        return false;
    }
}
