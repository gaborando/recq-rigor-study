package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat", indexes = @Index(name = "idx_seat_flight", columnList = "flightId"))
public class SeatEntity {
    @Id
    private String id;          // flightId + "#" + seat
    private String flightId;
    private String seat;
    private String status;      // AVAILABLE / HELD / BOOKED
    private String bookingId;

    public SeatEntity() {}
    public SeatEntity(String flightId, String seat, String status) {
        this.id = key(flightId, seat);
        this.flightId = flightId;
        this.seat = seat;
        this.status = status;
    }

    public static String key(String flightId, String seat) { return flightId + "#" + seat; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
