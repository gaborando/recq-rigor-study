package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ProductCreatedEvent extends DomainEvent {
    private String productId;
    private String name;
    private int unitPrice;
    private int stock;

    public ProductCreatedEvent() {}

    public ProductCreatedEvent(String productId, String name, int unitPrice, int stock) {
        this.productId = productId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
