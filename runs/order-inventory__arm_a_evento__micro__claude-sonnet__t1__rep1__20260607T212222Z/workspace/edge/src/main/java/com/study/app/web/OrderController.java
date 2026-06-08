package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.OrderStatsView;
import com.study.app.domain.view.OrderView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class OrderController {

    private final OrderInvoker invoker;

    public OrderController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(OrderInvoker.class);
    }

    record PlaceOrderRequest(String orderId, String customerId, String productId, Integer quantity) {}

    @PostMapping("/orders")
    public ResponseEntity<?> placeOrder(@RequestBody PlaceOrderRequest req) throws Exception {
        if (req.orderId() == null || req.orderId().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "orderId required"));
        if (req.customerId() == null || req.customerId().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "customerId required"));
        if (req.productId() == null || req.productId().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "productId required"));
        if (req.quantity() == null || req.quantity() < 1)
            return ResponseEntity.badRequest().body(Map.of("error", "quantity must be >= 1"));
        OrderView view = invoker.placeOrder(req.orderId(), req.customerId(), req.productId(), req.quantity());
        return ResponseEntity.status(202).body(view);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> getOrder(@PathVariable String id) throws Exception {
        OrderView view = invoker.getOrder(id);
        return ResponseEntity.ok(view);
    }

    @GetMapping("/stats/orders")
    public ResponseEntity<?> getStats() throws Exception {
        OrderStatsView stats = invoker.getStats();
        return ResponseEntity.ok(stats);
    }
}
