package com.study.app.web;

import com.study.app.command.CustomerCommandService;
import com.study.app.domain.Customer;
import com.study.app.domain.Notification;
import com.study.app.query.CustomerQueryService;
import com.study.app.query.NotificationQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
public class CustomerController {

    @Autowired
    private CustomerCommandService customerCommandService;
    @Autowired
    private CustomerQueryService customerQueryService;
    @Autowired
    private NotificationQueryService notificationQueryService;

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createCustomer(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Object balanceObj = body.get("balance");

        if (name == null || name.isBlank() || balanceObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required fields");
        }

        long balance;
        try {
            balance = ((Number) balanceObj).longValue();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid field types");
        }

        if (balance < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "balance must be >= 0");

        Customer c = customerCommandService.createCustomer(name, balance);
        return customerToMap(c);
    }

    @GetMapping("/customers/{id}")
    public Map<String, Object> getCustomer(@PathVariable String id) {
        return customerToMap(customerQueryService.getCustomer(parseUuidOrNotFound(id)));
    }

    @PostMapping("/customers/{id}/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deposit(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object amountObj = body.get("amount");
        if (amountObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount is required");
        }
        long amount;
        try {
            amount = ((Number) amountObj).longValue();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid amount");
        }
        if (amount < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be >= 1");
        customerCommandService.deposit(parseUuidOrNotFound(id), amount);
    }

    @GetMapping("/customers/{id}/notifications")
    public List<Map<String, Object>> getNotifications(@PathVariable String id) {
        return notificationQueryService.getNotifications(parseUuidOrNotFound(id)).stream()
            .map(this::notificationToMap)
            .toList();
    }

    private Map<String, Object> customerToMap(Customer c) {
        return Map.of(
            "id", c.getId().toString(),
            "name", c.getName(),
            "balance", c.getBalance()
        );
    }

    private Map<String, Object> notificationToMap(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", n.getOrderId().toString());
        m.put("status", n.getStatus().name());
        if (n.getReason() != null) {
            m.put("reason", n.getReason());
        }
        return m;
    }

    private UUID parseUuidOrNotFound(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }
    }
}
