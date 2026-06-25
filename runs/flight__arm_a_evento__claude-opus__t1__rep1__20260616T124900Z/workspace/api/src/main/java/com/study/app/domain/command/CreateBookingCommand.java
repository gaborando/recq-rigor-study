package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Client-supplied bookingId is the aggregate id, so creating the same booking
 *  twice is rejected by the framework (AggregateInitializedError) => idempotent. */
public class CreateBookingCommand extends DomainCommand {
    private String bookingId;
    private String customerId;
    private String flightId;
    private String seat;

    public CreateBookingCommand() {}
    public CreateBookingCommand(String bookingId, String customerId, String flightId, String seat) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.flightId = flightId;
        this.seat = seat;
    }

    @Override
    public String getAggregateId() { return bookingId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
}
