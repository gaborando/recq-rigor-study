package com.study.app.api.query;

import java.util.List;

/** Read-model DTOs returned by query handlers (serialized cross-service). */
public final class Views {

    private Views() {
    }

    public record SeatView(String seat, boolean available, String bookingId) {
    }

    public record FlightView(String id, int seatCount, int seatPrice, List<SeatView> seats) {
    }

    public record CustomerView(String id, String name, long balance) {
    }

    public record BookingView(String bookingId, String customerId, String flightId, String seat,
                              String status, String reason, Integer total) {
    }

    public record NotificationView(String bookingId, String status, String reason) {
    }

    /** Wrapper so the query response keeps its element type across Jackson serialization. */
    public record NotificationList(List<NotificationView> notifications) {
    }

    public record StatsView(long confirmed, long rejected, long revenue) {
    }
}
