package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Promotes a HELD seat to BOOKED (owned by the confirmed booking). */
public class ConfirmSeatCommand extends DomainCommand {
    private String flightId;
    private String seat;
    private String bookingId;

    public ConfirmSeatCommand() {}
    public ConfirmSeatCommand(String flightId, String seat, String bookingId) {
        this.flightId = flightId;
        this.seat = seat;
        this.bookingId = bookingId;
    }

    @Override
    public String getAggregateId() { return flightId; }

    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
