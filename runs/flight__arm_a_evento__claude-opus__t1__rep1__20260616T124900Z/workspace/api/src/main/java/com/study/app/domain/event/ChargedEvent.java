package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ChargedEvent extends DomainEvent {
    private String customerId;
    private long amount;
    private String bookingId;

    public ChargedEvent() {}
    public ChargedEvent(String customerId, long amount, String bookingId) {
        this.customerId = customerId;
        this.amount = amount;
        this.bookingId = bookingId;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
