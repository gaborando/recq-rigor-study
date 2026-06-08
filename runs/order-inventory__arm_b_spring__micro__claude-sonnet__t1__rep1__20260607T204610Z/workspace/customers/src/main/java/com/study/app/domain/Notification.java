package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private String status;

    private String reason;

    protected Notification() {}

    public Notification(UUID orderId, UUID customerId, String status, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
