package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class SeatView implements View {
    private String seat;
    private boolean available;
    private String bookingId; // present iff owned by a CONFIRMED booking

    public SeatView() {}
    public SeatView(String seat, boolean available, String bookingId) {
        this.seat = seat;
        this.available = available;
        this.bookingId = bookingId;
    }

    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
