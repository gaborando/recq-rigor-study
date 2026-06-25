package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ProductCreatedEvent extends DomainEvent {
    private String productId;
    private String name;
    private long unitPrice;
    private long stock;

    public ProductCreatedEvent() {}
    public ProductCreatedEvent(String productId, String name, long unitPrice, long stock) {
        this.productId = productId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }
    public long getStock() { return stock; }
    public void setStock(long stock) { this.stock = stock; }
}
