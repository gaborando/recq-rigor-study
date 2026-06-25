package com.study.app.api.query;

/** Query messages routed via Axon Server to the owning service's projection. */
public final class Queries {

    private Queries() {
    }

    public record FindFlight(String flightId) {
    }

    public record FindCustomer(String customerId) {
    }

    public record FindBooking(String bookingId) {
    }

    public record FindNotifications(String customerId) {
    }

    public record FindStats() {
    }
}
