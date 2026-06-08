package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class ConfirmOrderCommand extends DomainCommand {
    private String orderId;
    private int total;

    public ConfirmOrderCommand() {}

    public ConfirmOrderCommand(String orderId, int total) {
        this.orderId = orderId;
        this.total = total;
    }

    @Override
    public String getAggregateId() { return orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}
