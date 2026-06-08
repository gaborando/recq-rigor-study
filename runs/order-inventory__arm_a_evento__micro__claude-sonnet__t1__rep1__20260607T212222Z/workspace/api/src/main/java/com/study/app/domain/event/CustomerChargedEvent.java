package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class CustomerChargedEvent extends DomainEvent {
    private String customerId;
    private String orderId;
    private int amount;

    public CustomerChargedEvent() {}

    public CustomerChargedEvent(String customerId, String orderId, int amount) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
