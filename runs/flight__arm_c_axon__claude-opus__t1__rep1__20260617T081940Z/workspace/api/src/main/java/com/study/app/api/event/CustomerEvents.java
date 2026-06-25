package com.study.app.api.event;

/** Events emitted by the Customer aggregate (payments service). */
public final class CustomerEvents {

    private CustomerEvents() {
    }

    public record CustomerCreated(String customerId, String name, long balance) {
    }

    public record Deposited(String customerId, long amount) {
    }

    public record Charged(String customerId, long amount, String bookingId) {
    }

    public record ChargeRejected(String customerId, long amount, String bookingId) {
    }
}
