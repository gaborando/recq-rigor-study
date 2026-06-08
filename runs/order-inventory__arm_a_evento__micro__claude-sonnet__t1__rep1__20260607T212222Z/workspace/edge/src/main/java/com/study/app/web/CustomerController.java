package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
public class CustomerController {

    private final CustomerInvoker invoker;

    public CustomerController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(CustomerInvoker.class);
    }

    record CreateCustomerRequest(String name, Integer balance) {}

    record DepositRequest(Integer amount) {}

    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(@RequestBody CreateCustomerRequest req) throws Exception {
        if (req.name() == null || req.name().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "name required"));
        if (req.balance() == null || req.balance() < 0)
            return ResponseEntity.badRequest().body(Map.of("error", "balance must be >= 0"));
        CustomerView view = invoker.createCustomer(req.name(), req.balance());
        return ResponseEntity.status(201).body(view);
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable String id) throws Exception {
        CustomerView view = invoker.getCustomer(id);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<?> deposit(@PathVariable String id, @RequestBody DepositRequest req) throws Exception {
        if (req.amount() == null || req.amount() < 1)
            return ResponseEntity.badRequest().body(Map.of("error", "amount must be >= 1"));
        invoker.deposit(id, req.amount());
        return ResponseEntity.status(202).build();
    }

    @GetMapping("/customers/{id}/notifications")
    public ResponseEntity<?> getNotifications(@PathVariable String id) throws Exception {
        Collection<NotificationView> notifications = invoker.getNotifications(id);
        return ResponseEntity.ok(notifications);
    }
}
