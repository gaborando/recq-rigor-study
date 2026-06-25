package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class BookingCreatedEvent extends DomainEvent {
    private String bookingId;
    private String customerId;
    private String flightId;
    private String seat;

    public BookingCreatedEvent() {}
    public BookingCreatedEvent(String bookingId, String customerId, String flightId, String seat) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.flightId = flightId;
        this.seat = seat;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
}
