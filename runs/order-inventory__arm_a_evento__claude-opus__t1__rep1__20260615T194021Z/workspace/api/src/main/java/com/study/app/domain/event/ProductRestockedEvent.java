package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ProductRestockedEvent extends DomainEvent {
    private String productId;
    private long units;

    public ProductRestockedEvent() {}
    public ProductRestockedEvent(String productId, long units) {
        this.productId = productId;
        this.units = units;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public long getUnits() { return units; }
    public void setUnits(long units) { this.units = units; }
}
