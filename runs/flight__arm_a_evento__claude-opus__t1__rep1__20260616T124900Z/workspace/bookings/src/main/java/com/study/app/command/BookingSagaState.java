package com.study.app.command;

import com.evento.common.modeling.state.SagaState;

public class BookingSagaState extends SagaState {
    private String bookingId;
    private String phase;

    public BookingSagaState() {}

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getPhase() { return phase; }
    public void setPhase(String phase) { this.phase = phase; }
}
