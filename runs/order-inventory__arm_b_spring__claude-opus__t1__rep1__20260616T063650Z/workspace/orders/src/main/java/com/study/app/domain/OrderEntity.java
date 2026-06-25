package com.study.app.domain;

import jakarta.persistence.*;

/**
 * An order and its saga state. The client-supplied orderId is the primary key,
 * which is the idempotency guarantee: a duplicate/raced POST cannot create a
 * second order. {@code status} only ever moves PENDING -> CONFIRMED|REJECTED
 * (never regresses), and {@code notified} tracks whether the decision's
 * notification has been delivered so it can be retried independently.
 */
@Entity
@Table(name = "orders")
public class OrderEntity {

    public enum Status { PENDING, CONFIRMED, REJECTED }

    @Id
    @Column(length = 64)
    private String orderId;

    @Column(length = 64)
    private String customerId;

    @Column(length = 64)
    private String productId;

    private int quantity;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String reason;   // OUT_OF_STOCK | INSUFFICIENT_FUNDS, iff REJECTED
    private Long total;      // iff CONFIRMED

    private boolean notified;

    protected OrderEntity() {}

    public OrderEntity(String orderId, String customerId, String productId, int quantity) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = Status.PENDING;
        this.notified = false;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getTotal() { return total; }
    public boolean isNotified() { return notified; }
}
