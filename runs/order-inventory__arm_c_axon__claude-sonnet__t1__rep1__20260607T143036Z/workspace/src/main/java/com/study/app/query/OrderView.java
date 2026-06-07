package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_view")
public class OrderView {
    @Id
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private String status;
    private String reason;
    private Integer total;

    protected OrderView() {}

    public OrderView(String orderId, String customerId, String productId, int quantity, String status) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Integer getTotal() { return total; }

    public void setStatus(String status) { this.status = status; }
    public void setReason(String reason) { this.reason = reason; }
    public void setTotal(Integer total) { this.total = total; }
}
