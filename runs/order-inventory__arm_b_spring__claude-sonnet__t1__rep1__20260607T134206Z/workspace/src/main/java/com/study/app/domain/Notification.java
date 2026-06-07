package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String reason;

    protected Notification() {}

    public Notification(UUID orderId, UUID customerId, OrderStatus status, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public String getReason() { return reason; }
}
