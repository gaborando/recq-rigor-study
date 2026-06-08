package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class ChargeCustomerCommand extends DomainCommand {
    private String customerId;
    private String orderId;
    private int amount;

    public ChargeCustomerCommand() {}

    public ChargeCustomerCommand(String customerId, String orderId, int amount) {
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
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
