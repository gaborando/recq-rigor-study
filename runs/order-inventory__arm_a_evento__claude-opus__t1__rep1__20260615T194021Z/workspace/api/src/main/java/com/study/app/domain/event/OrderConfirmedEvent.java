package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderConfirmedEvent extends DomainEvent {
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private long total;

    public OrderConfirmedEvent() {}
    public OrderConfirmedEvent(String orderId, String customerId, String productId, int quantity, long total) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.total = total;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
