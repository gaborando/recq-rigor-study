package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class StockReservedEvent extends DomainEvent {
    private String productId;
    private String orderId;
    private int quantity;
    private int unitPrice;
    private long totalAmount;

    public StockReservedEvent() {}

    public StockReservedEvent(String productId, String orderId, int quantity, int unitPrice, long totalAmount) {
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalAmount = totalAmount;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }
    public long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(long totalAmount) { this.totalAmount = totalAmount; }
}
