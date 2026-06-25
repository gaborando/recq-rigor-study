package com.study.app.api.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/** Commands handled by the payments service (Customer aggregate). */
public final class CustomerCommands {

    private CustomerCommands() {
    }

    public record CreateCustomer(String customerId, String name, long balance) {
    }

    public record Deposit(@TargetAggregateIdentifier String customerId, long amount) {
    }

    /** Charge for a booking; idempotent on bookingId at the aggregate. */
    public record Charge(@TargetAggregateIdentifier String customerId, long amount, String bookingId) {
    }
}
