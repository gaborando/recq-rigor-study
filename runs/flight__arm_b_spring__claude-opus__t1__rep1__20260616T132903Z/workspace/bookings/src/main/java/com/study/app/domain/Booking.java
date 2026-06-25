package com.study.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "bookings")
public class Booking {
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";

    @Id
    @Column(name = "booking_id")
    private String bookingId;
    @Column(name = "customer_id")
    private String customerId;
    @Column(name = "flight_id")
    private String flightId;
    @Column(name = "seat")
    private String seat;
    @Column(name = "status")
    private String status;
    @Column(name = "reason")
    private String reason;
    @Column(name = "total")
    private Long total;
    @Column(name = "created_at")
    private Instant createdAt;

    protected Booking() {}

    public Booking(String bookingId, String customerId, String flightId, String seat) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.flightId = flightId;
        this.seat = seat;
        this.status = PENDING;
        this.createdAt = Instant.now();
    }

    public String getBookingId() { return bookingId; }
    public String getCustomerId() { return customerId; }
    public String getFlightId() { return flightId; }
    public String getSeat() { return seat; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getTotal() { return total; }
    public Instant getCreatedAt() { return createdAt; }
}
