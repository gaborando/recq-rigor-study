package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class ReserveStockCommand extends DomainCommand {
    private String productId;
    private String orderId;
    private int quantity;

    public ReserveStockCommand() {}
    public ReserveStockCommand(String productId, String orderId, int quantity) {
        this.productId = productId;
        this.orderId = orderId;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() { return productId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
