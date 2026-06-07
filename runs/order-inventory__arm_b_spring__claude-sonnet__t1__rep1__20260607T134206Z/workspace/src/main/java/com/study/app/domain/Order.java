package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID orderId;

    @Column(nullable = false)
    private UUID customerId;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String reason;

    private Long total;

    @Version
    private long version;

    protected Order() {}

    public Order(UUID orderId, UUID customerId, UUID productId, int quantity) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
    }

    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public OrderStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getTotal() { return total; }
    public long getVersion() { return version; }

    public void confirm(long total) {
        this.status = OrderStatus.CONFIRMED;
        this.total = total;
    }

    public void reject(String reason) {
        this.status = OrderStatus.REJECTED;
        this.reason = reason;
    }
}
