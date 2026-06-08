package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RestockProductCommand extends DomainCommand {
    private String productId;
    private int units;

    public RestockProductCommand() {}

    public RestockProductCommand(String productId, int units) {
        this.productId = productId;
        this.units = units;
    }

    @Override
    public String getAggregateId() { return productId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getUnits() { return units; }
    public void setUnits(int units) { this.units = units; }
}
