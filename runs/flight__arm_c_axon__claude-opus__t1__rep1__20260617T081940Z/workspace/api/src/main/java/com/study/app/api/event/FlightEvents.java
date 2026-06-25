package com.study.app.api.event;

import java.util.List;

/** Events emitted by the Flight aggregate (flights service). */
public final class FlightEvents {

    private FlightEvents() {
    }

    public record FlightCreated(String flightId, int seatCount, int seatPrice, List<String> seats) {
    }

    /** Seat won the distributed lock for this booking; carries the price for the saga. */
    public record SeatHeld(String flightId, String seat, String bookingId, int seatPrice) {
    }

    public record SeatReleased(String flightId, String seat, String bookingId) {
    }

    public record SeatBooked(String flightId, String seat, String bookingId) {
    }

    /** Seat already held/booked by another booking. */
    public record SeatHoldRejected(String flightId, String seat, String bookingId) {
    }
}
