package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class FundsDepositedEvent extends DomainEvent {
    private String customerId;
    private int amount;

    public FundsDepositedEvent() {}

    public FundsDepositedEvent(String customerId, int amount) {
        this.customerId = customerId;
        this.amount = amount;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
