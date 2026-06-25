package com.study.app.domain;

import jakarta.persistence.*;

/**
 * One notification per order decision, keyed by orderId so a re-delivered decision
 * is deduplicated by the primary key — exactly one notification per decision.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @Column(length = 64)
    private String orderId;

    @Column(length = 64)
    private String customerId;

    private String status;   // CONFIRMED | REJECTED
    private String reason;   // nullable

    protected Notification() {}

    public Notification(String orderId, String customerId, String status, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
