package com.study.app.command;

import com.evento.common.modeling.state.SagaState;

public class OrderSagaState extends SagaState {
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;

    public OrderSagaState() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
