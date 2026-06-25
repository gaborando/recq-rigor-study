package com.study.app.query;

import com.study.app.domain.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ProcessingGroup("customer-projection")
public class CustomerProjection {

    private final CustomerViewRepository customers;
    private final NotificationRepository notifications;

    public CustomerProjection(CustomerViewRepository customers, NotificationRepository notifications) {
        this.customers = customers;
        this.notifications = notifications;
    }

    // ---- balance view ----
    @EventHandler
    public void on(CustomerCreatedEvent e) {
        customers.save(new CustomerView(e.customerId(), e.name(), e.balance()));
    }

    @EventHandler
    public void on(FundsDepositedEvent e) {
        customers.findById(e.customerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() + e.amount());
            customers.save(c);
        });
    }

    @EventHandler
    public void on(ChargedEvent e) {
        customers.findById(e.customerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() - e.total());
            customers.save(c);
        });
    }

    // ---- notifications: exactly one per order decision (UNIQUE(orderId) guard) ----
    @EventHandler
    public void on(OrderConfirmedEvent e) {
        emit(e.orderId(), e.customerId(), "CONFIRMED", null);
    }

    @EventHandler
    public void on(OrderRejectedEvent e) {
        emit(e.orderId(), e.customerId(), "REJECTED", e.reason());
    }

    private void emit(String orderId, String customerId, String status, String reason) {
        if (notifications.findByCustomerId(customerId).stream()
                .anyMatch(n -> n.getOrderId().equals(orderId))) {
            return; // already notified
        }
        try {
            notifications.saveAndFlush(new NotificationView(orderId, customerId, status, reason));
        } catch (DataIntegrityViolationException duplicate) {
            // concurrent/redelivered event hit the UNIQUE(orderId) constraint -> exactly once
        }
    }

    // ---- queries ----
    @QueryHandler
    public CustomerDto handle(com.study.app.query.FindCustomer q) {
        return customers.findById(q.customerId())
                .map(c -> new CustomerDto(c.getId(), c.getName(), c.getBalance()))
                .orElse(null);
    }

    @QueryHandler
    public NotificationList handle(com.study.app.query.FindNotifications q) {
        return new NotificationList(notifications.findByCustomerId(q.customerId()).stream()
                .map(n -> new NotificationDto(n.getOrderId(), n.getStatus(), n.getReason()))
                .toList());
    }
}
