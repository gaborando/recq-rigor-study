package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class SeatBookedEvent extends DomainEvent {
    private String flightId;
    private String seat;
    private String bookingId;

    public SeatBookedEvent() {}
    public SeatBookedEvent(String flightId, String seat, String bookingId) {
        this.flightId = flightId;
        this.seat = seat;
        this.bookingId = bookingId;
    }

    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
