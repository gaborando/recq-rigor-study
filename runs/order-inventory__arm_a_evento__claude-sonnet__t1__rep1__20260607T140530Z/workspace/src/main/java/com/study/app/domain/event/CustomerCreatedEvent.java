package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class CustomerCreatedEvent extends DomainEvent {
    private String customerId;
    private String name;
    private int balance;

    public CustomerCreatedEvent() {}

    public CustomerCreatedEvent(String customerId, String name, int balance) {
        this.customerId = customerId;
        this.name = name;
        this.balance = balance;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
}
