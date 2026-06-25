package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Targets the flight aggregate; the per-aggregate lock makes the seat HOLD the
 *  distributed lock — exactly one concurrent requester wins a given seat. */
public class HoldSeatCommand extends DomainCommand {
    private String flightId;
    private String seat;
    private String bookingId;

    public HoldSeatCommand() {}
    public HoldSeatCommand(String flightId, String seat, String bookingId) {
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
