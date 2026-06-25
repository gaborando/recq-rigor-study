package com.study.app.domain;

import jakarta.persistence.*;

/**
 * Idempotency record for a charge attempt, keyed by orderId. Persisting it makes
 * charging replayable: a retried charge for the same orderId returns the stored
 * outcome instead of debiting the balance a second time.
 */
@Entity
@Table(name = "charge")
public class Charge {

    public enum Status { CHARGED, REJECTED_INSUFFICIENT, REJECTED_UNKNOWN }

    @Id
    @Column(length = 64)
    private String orderId;

    @Column(length = 64)
    private String customerId;

    private long amount;

    @Enumerated(EnumType.STRING)
    private Status status;

    protected Charge() {}

    public Charge(String orderId, String customerId, long amount, Status status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public long getAmount() { return amount; }
    public Status getStatus() { return status; }
}
