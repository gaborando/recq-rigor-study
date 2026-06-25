package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RejectBookingCommand extends DomainCommand {
    private String bookingId;
    private String reason;

    public RejectBookingCommand() {}
    public RejectBookingCommand(String bookingId, String reason) {
        this.bookingId = bookingId;
        this.reason = reason;
    }

    @Override
    public String getAggregateId() { return bookingId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
