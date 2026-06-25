package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class BookingConfirmedEvent extends DomainEvent {
    private String bookingId;
    private String customerId;
    private long total;

    public BookingConfirmedEvent() {}
    public BookingConfirmedEvent(String bookingId, String customerId, long total) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.total = total;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
