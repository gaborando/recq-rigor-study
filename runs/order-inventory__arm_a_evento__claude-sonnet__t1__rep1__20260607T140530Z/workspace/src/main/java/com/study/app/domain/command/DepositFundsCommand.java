package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class DepositFundsCommand extends DomainCommand {
    private String customerId;
    private int amount;

    public DepositFundsCommand() {}

    public DepositFundsCommand(String customerId, int amount) {
        this.customerId = customerId;
        this.amount = amount;
    }

    @Override
    public String getAggregateId() { return customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
