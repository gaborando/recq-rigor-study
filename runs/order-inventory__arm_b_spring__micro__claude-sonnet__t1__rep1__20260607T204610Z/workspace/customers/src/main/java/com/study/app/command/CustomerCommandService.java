package com.study.app.command;

import com.study.app.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class CustomerCommandService {

    private final CustomerRepository customers;
    private final NotificationRepository notifications;

    public CustomerCommandService(CustomerRepository customers, NotificationRepository notifications) {
        this.customers = customers;
        this.notifications = notifications;
    }

    @Transactional
    public Customer create(String name, int balance) {
        return customers.save(new Customer(name, balance));
    }

    @Transactional
    public void deposit(UUID id, int amount) {
        if (!customers.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "customer not found");
        }
        customers.addBalance(id, amount);
    }

    @Transactional
    public boolean charge(UUID customerId, int amount) {
        return customers.charge(customerId, amount) > 0;
    }

    @Transactional
    public void notify(UUID customerId, UUID orderId, String status, String reason) {
        try {
            notifications.saveAndFlush(new Notification(orderId, customerId, status, reason));
        } catch (DataIntegrityViolationException ignored) {
            // idempotent: already notified
        }
    }
}
