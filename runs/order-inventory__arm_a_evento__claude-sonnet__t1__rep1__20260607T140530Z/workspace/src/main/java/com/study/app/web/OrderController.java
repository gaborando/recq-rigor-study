package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.OrderView;
import com.study.app.domain.view.StatsView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@RestController
public class OrderController {

    private final OrderInvoker orderInvoker;

    public OrderController(EventoBundle eventoBundle) {
        this.orderInvoker = eventoBundle.getInvoker(OrderInvoker.class);
    }

    record CreateOrderRequest(String orderId, String customerId, String productId, Integer quantity) {}

    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(@RequestBody CreateOrderRequest r) {
        if (r.orderId() == null || r.orderId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "orderId is required"));
        }
        if (r.customerId() == null || r.customerId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "customerId is required"));
        }
        if (r.productId() == null || r.productId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "productId is required"));
        }
        if (r.quantity() == null || r.quantity() < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "quantity must be >= 1"));
        }
        try {
            OrderView view = orderInvoker.placeOrder(r.orderId(), r.customerId(), r.productId(), r.quantity());
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(view);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) {
        try {
            OrderView view = orderInvoker.getOrder(id);
            return ResponseEntity.ok(view);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/stats/orders")
    public ResponseEntity<?> getStats() {
        try {
            StatsView stats = orderInvoker.getStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
