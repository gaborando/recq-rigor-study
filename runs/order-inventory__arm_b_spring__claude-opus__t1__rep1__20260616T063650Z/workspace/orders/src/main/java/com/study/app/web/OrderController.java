package com.study.app.web;

import com.study.app.command.OrderService;
import com.study.app.domain.OrderEntity;
import com.study.app.query.Stats;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.study.app.web.Validation.atLeast;
import static com.study.app.web.Validation.notBlank;

@RestController
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    public ResponseEntity<Dtos.OrderView> place(@RequestBody Dtos.CreateOrder body) {
        notBlank(body.orderId(), "orderId");
        notBlank(body.customerId(), "customerId");
        notBlank(body.productId(), "productId");
        atLeast(body.quantity(), 1, "quantity");

        OrderEntity o = service.create(body.orderId(), body.customerId(), body.productId(), body.quantity());
        service.processAsync(o.getOrderId());   // kick async saga (also fine for an idempotent replay)
        return ResponseEntity.accepted().body(Dtos.OrderView.of(o));
    }

    @GetMapping("/orders/{id}")
    public Dtos.OrderView get(@PathVariable String id) {
        OrderEntity o = service.find(id);
        if (o == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown order");
        return Dtos.OrderView.of(o);
    }

    @GetMapping("/stats/orders")
    public Dtos.StatsView stats() {
        Stats s = service.stats();
        return new Dtos.StatsView(s.confirmed(), s.rejected(), s.revenue());
    }
}
