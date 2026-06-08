package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderRejectedEvent extends DomainEvent {
    private String orderId;
    private String customerId;
    private String reason;

    public OrderRejectedEvent() {}

    public OrderRejectedEvent(String orderId, String customerId, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
