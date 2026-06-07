package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class OrderConfirmedEvent extends DomainEvent {
    private String orderId;
    private String customerId;
    private long total;

    public OrderConfirmedEvent() {}

    public OrderConfirmedEvent(String orderId, String customerId, long total) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.total = total;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
