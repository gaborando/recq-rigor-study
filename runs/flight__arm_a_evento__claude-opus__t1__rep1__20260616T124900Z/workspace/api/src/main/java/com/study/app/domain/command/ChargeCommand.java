package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

/** Targets the customer aggregate; per-aggregate serialization makes the
 *  check-and-debit atomic, so funds are never double-spent or negative. */
public class ChargeCommand extends DomainCommand {
    private String customerId;
    private long amount;
    private String bookingId;

    public ChargeCommand() {}
    public ChargeCommand(String customerId, long amount, String bookingId) {
        this.customerId = customerId;
        this.amount = amount;
        this.bookingId = bookingId;
    }

    @Override
    public String getAggregateId() { return customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public long getAmount() { return amount; }
    public void setAmount(long amount) { this.amount = amount; }
    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
