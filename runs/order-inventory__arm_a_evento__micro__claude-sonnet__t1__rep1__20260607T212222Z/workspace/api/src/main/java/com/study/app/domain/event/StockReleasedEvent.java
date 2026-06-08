package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class StockReleasedEvent extends DomainEvent {
    private String productId;
    private String orderId;
    private int quantity;

    public StockReleasedEvent() {}

    public StockReleasedEvent(String productId, String orderId, int quantity) {
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
