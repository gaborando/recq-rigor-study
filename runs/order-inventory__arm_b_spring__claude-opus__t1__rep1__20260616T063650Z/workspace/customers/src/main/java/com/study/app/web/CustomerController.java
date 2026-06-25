package com.study.app.web;

import com.study.app.command.CustomerService;
import com.study.app.command.CustomerService.ChargeOutcome;
import com.study.app.domain.Customer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.study.app.web.Validation.atLeast;
import static com.study.app.web.Validation.notBlank;

@RestController
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    // ---- public customer API ----

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.CustomerView create(@RequestBody Dtos.CreateCustomer body) {
        notBlank(body.name(), "name");
        atLeast(body.balance(), 0, "balance");
        return view(service.createCustomer(body.name(), body.balance()));
    }

    @GetMapping("/customers/{id}")
    public Dtos.CustomerView get(@PathVariable String id) {
        Customer c = service.find(id);
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown customer");
        return view(c);
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<Void> deposit(@PathVariable String id, @RequestBody Dtos.Deposit body) {
        atLeast(body.amount(), 1, "amount");
        if (!service.deposit(id, body.amount())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown customer");
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/customers/{id}/notifications")
    public List<Dtos.NotificationView> notifications(@PathVariable String id) {
        if (service.find(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown customer");
        }
        return service.notificationsFor(id).stream()
                .map(n -> new Dtos.NotificationView(n.getOrderId(), n.getStatus(), n.getReason()))
                .toList();
    }

    // ---- internal saga API ----

    @PostMapping("/internal/charges")
    public Dtos.ChargeResponse charge(@RequestBody Dtos.ChargeRequest body) {
        notBlank(body.orderId(), "orderId");
        notBlank(body.customerId(), "customerId");
        atLeast(body.amount(), 1, "amount");
        ChargeOutcome o = service.charge(body.orderId(), body.customerId(), body.amount());
        return new Dtos.ChargeResponse(o.name());
    }

    @PostMapping("/internal/notifications")
    public ResponseEntity<Void> notify(@RequestBody Dtos.NotifyRequest body) {
        service.notify(body.orderId(), body.customerId(), body.status(), body.reason());
        return ResponseEntity.ok().build();
    }

    private static Dtos.CustomerView view(Customer c) {
        return new Dtos.CustomerView(c.getId(), c.getName(), c.getBalance());
    }
}
