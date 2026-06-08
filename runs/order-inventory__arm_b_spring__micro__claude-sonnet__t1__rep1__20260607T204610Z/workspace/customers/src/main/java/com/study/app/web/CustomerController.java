package com.study.app.web;

import com.study.app.command.CustomerCommandService;
import com.study.app.domain.Customer;
import com.study.app.domain.CustomerRepository;
import com.study.app.domain.NotificationRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;

@RestController
public class CustomerController {

    private final CustomerCommandService commandService;
    private final CustomerRepository customers;
    private final NotificationRepository notifications;

    public CustomerController(CustomerCommandService commandService, CustomerRepository customers, NotificationRepository notifications) {
        this.commandService = commandService;
        this.customers = customers;
        this.notifications = notifications;
    }

    record CreateCustomerRequest(@NotBlank String name, @NotNull @Min(0) Integer balance) {}
    record DepositRequest(@NotNull @Min(1) Integer amount) {}
    record CustomerView(UUID id, String name, int balance) {}
    record NotificationView(UUID orderId, String status, String reason) {}

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerView create(@RequestBody @Valid CreateCustomerRequest req) {
        Customer c = commandService.create(req.name(), req.balance());
        return new CustomerView(c.getId(), c.getName(), c.getBalance());
    }

    @GetMapping("/customers/{id}")
    public CustomerView get(@PathVariable String id) {
        UUID uid = parseUuid(id);
        return customers.findById(uid)
            .map(c -> new CustomerView(c.getId(), c.getName(), c.getBalance()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found"));
    }

    @PostMapping("/customers/{id}/deposit")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void deposit(@PathVariable String id, @RequestBody @Valid DepositRequest req) {
        commandService.deposit(parseUuid(id), req.amount());
    }

    @GetMapping("/customers/{id}/notifications")
    public List<NotificationView> getNotifications(@PathVariable String id) {
        UUID uid = parseUuid(id);
        if (!customers.existsById(uid)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        return notifications.findByCustomerId(uid).stream()
            .map(n -> new NotificationView(n.getOrderId(), n.getStatus(), n.getReason()))
            .toList();
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
    }
}
