package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class DepositedEvent extends DomainEvent {
    private String customerId;
    private long amount;

    public DepositedEvent() {}
    public DepositedEvent(String customerId, long amount) {
        this.customerId = customerId;
        this.amount = amount;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
