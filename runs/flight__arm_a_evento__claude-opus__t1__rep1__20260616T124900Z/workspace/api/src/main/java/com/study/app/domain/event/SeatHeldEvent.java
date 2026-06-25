package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/** Carries the flight's seatPrice so the saga can charge without a separate query. */
public class SeatHeldEvent extends DomainEvent {
    private String flightId;
    private String seat;
    private String bookingId;
    private int seatPrice;

    public SeatHeldEvent() {}
    public SeatHeldEvent(String flightId, String seat, String bookingId, int seatPrice) {
        this.flightId = flightId;
        this.seat = seat;
        this.bookingId = bookingId;
        this.seatPrice = seatPrice;
    }

    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public int getSeatPrice() { return seatPrice; }
    public void setSeatPrice(int seatPrice) { this.seatPrice = seatPrice; }
}
