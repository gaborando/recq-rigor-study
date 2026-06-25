package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateOrderCommand extends DomainCommand {
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;

    public CreateOrderCommand() {}
    public CreateOrderCommand(String orderId, String customerId, String productId, int quantity) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
    }

    @Override
    public String getAggregateId() { return orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
