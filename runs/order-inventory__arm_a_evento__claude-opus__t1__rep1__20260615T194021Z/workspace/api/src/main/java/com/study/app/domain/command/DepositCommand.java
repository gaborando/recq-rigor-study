package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class DepositCommand extends DomainCommand {
    private String customerId;
    private long amount;

    public DepositCommand() {}
    public DepositCommand(String customerId, long amount) {
        this.customerId = customerId;
        this.amount = amount;
    }

    @Override
    public String getAggregateId() { return customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
