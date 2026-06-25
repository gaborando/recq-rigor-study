package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class BookingView implements View {
    private String bookingId;
    private String customerId;
    private String flightId;
    private String seat;
    private String status;
    private String reason; // present iff REJECTED
    private Long total;    // present iff CONFIRMED

    public BookingView() {}

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }
}
