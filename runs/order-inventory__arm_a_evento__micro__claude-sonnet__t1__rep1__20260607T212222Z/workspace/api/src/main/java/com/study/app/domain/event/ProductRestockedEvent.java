package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ProductRestockedEvent extends DomainEvent {
    private String productId;
    private int units;

    public ProductRestockedEvent() {}

    public ProductRestockedEvent(String productId, int units) {
        this.productId = productId;
        this.units = units;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
}
