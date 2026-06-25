package com.study.app.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * One seat on a flight. The (flightId, label) pair is unique — that uniqueness
 * plus a pessimistic row lock is the per-seat distributed lock: at most one
 * booking can ever hold or own a given seat.
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"flightId", "label"}),
        indexes = @Index(name = "idx_seat_flight", columnList = "flightId"))
public class Seat {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String HELD = "HELD";
    public static final String BOOKED = "BOOKED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String flightId;

    @Column(nullable = false)
    private String label;

    private int idx;

    @Column(nullable = false)
    private String status = AVAILABLE;

    private String holdBookingId;
    private Instant holdExpiresAt;
    private String ownerBookingId;

    protected Seat() {}

    public Seat(String flightId, String label, int idx) {
        this.flightId = flightId;
        this.label = label;
        this.idx = idx;
    }

    /** A seat is effectively free if available, or held by an expired hold. */
    public boolean isEffectivelyAvailable(Instant now) {
        if (BOOKED.equals(status)) return false;
        if (HELD.equals(status)) {
            return holdExpiresAt != null && holdExpiresAt.isBefore(now);
        }
        return true;
    }

    public boolean isHeldActive(Instant now) {
        return HELD.equals(status) && holdExpiresAt != null && !holdExpiresAt.isBefore(now);
    }

    public Long getId() { return id; }
    public String getFlightId() { return flightId; }
    public String getLabel() { return label; }
    public int getIdx() { return idx; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getHoldBookingId() { return holdBookingId; }
    public void setHoldBookingId(String v) { this.holdBookingId = v; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
    public void setHoldExpiresAt(Instant v) { this.holdExpiresAt = v; }
    public String getOwnerBookingId() { return ownerBookingId; }
    public void setOwnerBookingId(String v) { this.ownerBookingId = v; }
}
