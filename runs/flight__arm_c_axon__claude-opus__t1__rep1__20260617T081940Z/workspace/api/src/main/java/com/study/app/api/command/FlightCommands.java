package com.study.app.api.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Commands handled by the flights service (Flight aggregate). */
public final class FlightCommands {

    private FlightCommands() {
    }

    public record CreateFlight(String flightId, int seatCount, int seatPrice) {
    }

    /** HOLD the seat — the distributed lock. */
    public record HoldSeat(@TargetAggregateIdentifier String flightId, String seat, String bookingId) {
    }

    /** Compensation: release a previously held seat. */
    public record ReleaseSeat(@TargetAggregateIdentifier String flightId, String seat, String bookingId) {
    }

    /** Promote a held seat to permanently booked/owned. */
    public record ConfirmSeat(@TargetAggregateIdentifier String flightId, String seat, String bookingId) {
    }
}
