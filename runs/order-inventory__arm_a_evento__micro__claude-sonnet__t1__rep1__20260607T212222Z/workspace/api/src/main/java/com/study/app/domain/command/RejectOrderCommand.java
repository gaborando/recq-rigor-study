package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RejectOrderCommand extends DomainCommand {
    private String orderId;
    private String reason;

    public RejectOrderCommand() {}

    public RejectOrderCommand(String orderId, String reason) {
        this.orderId = orderId;
        this.reason = reason;
    }

    @Override
    public String getAggregateId() { return orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
