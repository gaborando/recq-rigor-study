package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class ConfirmBookingCommand extends DomainCommand {
    private String bookingId;
    private long total;

    public ConfirmBookingCommand() {}
    public ConfirmBookingCommand(String bookingId, long total) {
        this.bookingId = bookingId;
        this.total = total;
    }

    @Override
    public String getAggregateId() { return bookingId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
