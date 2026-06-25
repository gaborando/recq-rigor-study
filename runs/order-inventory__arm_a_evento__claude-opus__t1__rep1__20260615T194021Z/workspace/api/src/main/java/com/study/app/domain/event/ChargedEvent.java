package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ChargedEvent extends DomainEvent {
    private String customerId;
    private String orderId;
    private long amount;

    public ChargedEvent() {}
    public ChargedEvent(String customerId, String orderId, long amount) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
