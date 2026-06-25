package com.study.app.api.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Commands handled by the bookings service (Booking aggregate). */
public final class BookingCommands {

    private BookingCommands() {
    }

    /** Client-supplied bookingId is the aggregate id; idempotent on replay. */
    public record CreateBooking(@TargetAggregateIdentifier String bookingId,
                                String customerId, String flightId, String seat) {
    }

    public record ConfirmBooking(@TargetAggregateIdentifier String bookingId, int total) {
    }

    public record RejectBooking(@TargetAggregateIdentifier String bookingId, String reason) {
    }
}
