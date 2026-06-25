package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat_view", indexes = @Index(name = "idx_seat_flight", columnList = "flightId"))
public class SeatRow {

    @Id
    private String key;          // flightId + ":" + seat
    private String flightId;
    private String seat;
    private boolean available;
    private String bookingId;    // non-null only once owned by a CONFIRMED booking

    protected SeatRow() {
    }

    public SeatRow(String flightId, String seat) {
        this.key = flightId + ":" + seat;
        this.flightId = flightId;
        this.seat = seat;
        this.available = true;
        this.bookingId = null;
    }

    public String getSeat() {
        return seat;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getBookingId() {
        return bookingId;
    }

    public void setBookingId(String bookingId) {
        this.bookingId = bookingId;
    }
}
