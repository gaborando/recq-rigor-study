package com.study.app.domain;

import jakarta.persistence.*;

/**
 * Idempotency record for a reserve attempt, keyed by the client-supplied orderId.
 * Persisting it under the orderId primary key makes reserve/release/confirm
 * replayable: a retried reserve with the same orderId returns the stored outcome
 * instead of decrementing stock a second time.
 */
@Entity
@Table(name = "reservation")
public class Reservation {

    public enum Status {
        RESERVED,          // stock decremented, holding units
        CONFIRMED,         // reservation consumed (order confirmed)
        RELEASED,          // reservation compensated (stock added back)
        REJECTED_OOS,      // reserve failed: insufficient stock
        REJECTED_UNKNOWN   // reserve failed: unknown product
    }

    @Id
    @Column(length = 64)
    private String orderId;

    @Column(length = 64)
    private String productId;

    private int quantity;
    private int unitPrice;

    @Enumerated(EnumType.STRING)
    private Status status;

    protected Reservation() {}

    public Reservation(String orderId, String productId, int quantity, int unitPrice, Status status) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public int getUnitPrice() { return unitPrice; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
