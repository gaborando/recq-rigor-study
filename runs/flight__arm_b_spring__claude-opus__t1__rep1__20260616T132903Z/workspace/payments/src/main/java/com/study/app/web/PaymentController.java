package com.study.app.web;

import com.study.app.command.PaymentService;
import com.study.app.domain.Customer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/customers")
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) return ResponseEntity.badRequest().build();
        Object name = body.get("name");
        Long balance = longOrNull(body, "balance");
        if (!(name instanceof String s) || s.isEmpty() || balance == null || balance < 0) {
            return ResponseEntity.badRequest().build();
        }
        Customer c = service.createCustomer(s, balance);
        return ResponseEntity.status(HttpStatus.CREATED).body(view(c));
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return service.getCustomer(id)
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(view(c)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<?> deposit(@PathVariable String id,
                                     @RequestBody(required = false) Map<String, Object> body) {
        Long amount = longOrNull(body, "amount");
        if (amount == null || amount < 1) return ResponseEntity.badRequest().build();
        if (!service.deposit(id, amount)) return ResponseEntity.notFound().build();
        return ResponseEntity.accepted().build();
    }

    // ---- internal saga endpoints (called by the bookings service) ----

    @PostMapping("/internal/charge")
    public Map<String, Object> charge(@RequestBody Map<String, Object> body) {
        PaymentService.ChargeResult r = service.charge(
                (String) body.get("bookingId"),
                (String) body.get("customerId"),
                ((Number) body.get("amount")).longValue());
        return Map.of("result", r.name());
    }

    @PostMapping("/internal/refund")
    public Map<String, Object> refund(@RequestBody Map<String, Object> body) {
        service.refund((String) body.get("bookingId"));
        return Map.of("ok", true);
    }

    private static Map<String, Object> view(Customer c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("balance", c.getBalance());
        return m;
    }

    private static Long longOrNull(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        if (v instanceof Number n) return n.longValue();
        return null;
    }
}
