package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class StockRestockedEvent extends DomainEvent {
    private String productId;
    private int units;

    public StockRestockedEvent() {}

    public StockRestockedEvent(String productId, int units) {
        this.productId = productId;
        this.units = units;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
}
