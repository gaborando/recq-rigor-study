package com.study.app.web;

/** Request/response payloads for the customers HTTP API. */
public final class Dtos {
    private Dtos() {}

    public record CreateCustomer(String name, Integer balance) {}

    public record Deposit(Integer amount) {}

    public record CustomerView(String id, String name, long balance) {}

    public record NotificationView(String orderId, String status, String reason) {}

    // ---- internal saga DTOs ----
    public record ChargeRequest(String orderId, String customerId, Integer amount) {}

    public record ChargeResponse(String outcome) {}

    public record NotifyRequest(String orderId, String customerId, String status, String reason) {}
}
