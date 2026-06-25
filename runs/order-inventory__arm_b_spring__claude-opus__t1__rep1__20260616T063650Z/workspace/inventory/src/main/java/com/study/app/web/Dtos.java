package com.study.app.web;

/** Request/response payloads for the inventory HTTP API. */
public final class Dtos {
    private Dtos() {}

    public record CreateProduct(String name, Integer unitPrice, Integer stock) {}

    public record Restock(Integer units) {}

    public record ProductView(String id, String name, int unitPrice, long stock) {}

    // ---- internal saga DTOs ----
    public record ReserveRequest(String orderId, String productId, Integer quantity) {}

    public record ReserveResponse(String outcome, int unitPrice) {}
}
