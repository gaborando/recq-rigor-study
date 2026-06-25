package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class ChargeCommand extends DomainCommand {
    private String customerId;
    private String orderId;
    private long amount;

    public ChargeCommand() {}
    public ChargeCommand(String customerId, String orderId, long amount) {
        this.customerId = customerId;
        this.orderId = orderId;
        this.amount = amount;
    }

    @Override
    public String getAggregateId() { return customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
}
