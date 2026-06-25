package com.study.app.command;

import com.study.app.domain.Charge;
import com.study.app.domain.Customer;
import com.study.app.domain.Notification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    public enum ChargeOutcome { CHARGED, INSUFFICIENT_FUNDS, UNKNOWN_CUSTOMER }

    private final CustomerRepository customers;
    private final ChargeRepository charges;
    private final NotificationRepository notifications;

    public CustomerService(CustomerRepository customers, ChargeRepository charges,
                           NotificationRepository notifications) {
        this.customers = customers;
        this.charges = charges;
        this.notifications = notifications;
    }

    @Transactional
    public Customer createCustomer(String name, long balance) {
        return customers.save(new Customer(UUID.randomUUID().toString(), name, balance));
    }

    /** @return true if applied, false if the customer is unknown. */
    @Transactional
    public boolean deposit(String customerId, long amount) {
        return customers.addBalance(customerId, amount) == 1;
    }

    @Transactional(readOnly = true)
    public Customer find(String customerId) {
        return customers.findById(customerId).orElse(null);
    }

    /**
     * Charge {@code amount} to {@code customerId} for {@code orderId}. Idempotent:
     * a repeat call with the same orderId returns the original outcome without
     * debiting again. Never overdraws (balance never goes negative).
     */
    @Transactional
    public ChargeOutcome charge(String orderId, String customerId, long amount) {
        Charge existing = charges.findById(orderId).orElse(null);
        if (existing != null) {
            return toOutcome(existing.getStatus());
        }

        // Read the customer ONLY through the locking query, so the balance is the
        // freshly-locked row state. Reading it unlocked first would return a stale
        // cached balance and allow double-spend under concurrency.
        Customer locked = customers.findByIdForUpdate(customerId).orElse(null);
        if (locked == null) {
            return persist(new Charge(orderId, customerId, amount, Charge.Status.REJECTED_UNKNOWN));
        }

        existing = charges.findById(orderId).orElse(null);
        if (existing != null) {
            return toOutcome(existing.getStatus());
        }

        if (locked.getBalance() >= amount) {
            locked.setBalance(locked.getBalance() - amount);
            customers.save(locked);
            return persist(new Charge(orderId, customerId, amount, Charge.Status.CHARGED));
        }
        return persist(new Charge(orderId, customerId, amount, Charge.Status.REJECTED_INSUFFICIENT));
    }

    /** Record exactly one notification for an order decision. Idempotent via PK. */
    @Transactional
    public void notify(String orderId, String customerId, String status, String reason) {
        try {
            notifications.saveAndFlush(new Notification(orderId, customerId, status, reason));
        } catch (DataIntegrityViolationException already) {
            // exactly-once: a notification for this order already exists
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> notificationsFor(String customerId) {
        return notifications.findByCustomerId(customerId);
    }

    private ChargeOutcome persist(Charge c) {
        try {
            charges.saveAndFlush(c);
            return toOutcome(c.getStatus());
        } catch (DataIntegrityViolationException dup) {
            return toOutcome(charges.findById(c.getOrderId()).orElseThrow().getStatus());
        }
    }

    private ChargeOutcome toOutcome(Charge.Status s) {
        return switch (s) {
            case CHARGED -> ChargeOutcome.CHARGED;
            case REJECTED_INSUFFICIENT -> ChargeOutcome.INSUFFICIENT_FUNDS;
            case REJECTED_UNKNOWN -> ChargeOutcome.UNKNOWN_CUSTOMER;
        };
    }
}
