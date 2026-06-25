package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class ConfirmOrderCommand extends DomainCommand {
    private String orderId;
    private long total;

    public ConfirmOrderCommand() {}
    public ConfirmOrderCommand(String orderId, long total) {
        this.orderId = orderId;
        this.total = total;
    }

    @Override
    public String getAggregateId() { return orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
