package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateCustomerCommand extends DomainCommand {
    private String customerId;
    private String name;
    private int balance;

    public CreateCustomerCommand() {}

    public CreateCustomerCommand(String customerId, String name, int balance) {
        this.customerId = customerId;
        this.name = name;
        this.balance = balance;
    }

    @Override
    public String getAggregateId() { return customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
}
