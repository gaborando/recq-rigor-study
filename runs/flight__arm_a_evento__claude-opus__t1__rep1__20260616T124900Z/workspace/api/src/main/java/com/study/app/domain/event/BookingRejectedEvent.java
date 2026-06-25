package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class BookingRejectedEvent extends DomainEvent {
    private String bookingId;
    private String customerId;
    private String reason;

    public BookingRejectedEvent() {}
    public BookingRejectedEvent(String bookingId, String customerId, String reason) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.reason = reason;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
