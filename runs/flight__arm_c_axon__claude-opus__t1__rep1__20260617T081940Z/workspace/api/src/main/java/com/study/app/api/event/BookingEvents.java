package com.study.app.api.event;

/** Events emitted by the Booking aggregate (bookings service). */
public final class BookingEvents {

    private BookingEvents() {
    }

    public record BookingCreated(String bookingId, String customerId, String flightId, String seat) {
    }

    public record BookingConfirmed(String bookingId, String customerId, int total) {
    }

    public record BookingRejected(String bookingId, String customerId, String reason) {
    }
}
