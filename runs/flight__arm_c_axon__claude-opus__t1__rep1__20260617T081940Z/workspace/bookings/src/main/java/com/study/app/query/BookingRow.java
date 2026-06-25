package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "booking_view")
public class BookingRow {

    @Id
    private String bookingId;
    private String customerId;
    private String flightId;
    private String seat;
    private String status;
    private String reason;
    private Integer total;

    protected BookingRow() {
    }

    public BookingRow(String bookingId, String customerId, String flightId, String seat, String status) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.flightId = flightId;
        this.seat = seat;
        this.status = status;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getSeat() {
        return seat;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }
}
