package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private String status; // PENDING, CONFIRMED, REJECTED

    private String reason; // OUT_OF_STOCK, INSUFFICIENT_FUNDS

    private Integer total;

    @Version
    private long version;

    protected Order() {}

    public Order(UUID id, UUID customerId, UUID productId, int quantity) {
        this.id = id;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = "PENDING";
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Integer getTotal() { return total; }

    public void confirm(int total) {
        this.status = "CONFIRMED";
        this.total = total;
    }

    public void reject(String reason) {
        this.status = "REJECTED";
        this.reason = reason;
    }
}
