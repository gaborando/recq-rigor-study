package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RestockProductCommand extends DomainCommand {
    private String productId;
    private long units;

    public RestockProductCommand() {}
    public RestockProductCommand(String productId, long units) {
        this.productId = productId;
        this.units = units;
    }

    @Override
    public String getAggregateId() { return productId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public long getUnits() { return units; }
    public void setUnits(long units) { this.units = units; }
}
