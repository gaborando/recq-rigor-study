package com.study.app.command;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * REST client for the inventory service. Reserve/release/confirm are all keyed by
 * orderId on the inventory side, so retrying any of them is safe.
 */
@Component
public class InventoryClient {

    public enum ReserveOutcome { RESERVED, OUT_OF_STOCK, UNKNOWN_PRODUCT }

    public record ReserveResult(ReserveOutcome outcome, int unitPrice) {}

    private final RestClient client;

    public InventoryClient(RestClient inventoryRestClient) {
        this.client = inventoryRestClient;
    }

    public ReserveResult reserve(String orderId, String productId, int quantity) {
        Map<String, Object> resp = client.post()
                .uri("/internal/reservations")
                .body(Map.of("orderId", orderId, "productId", productId, "quantity", quantity))
                .retrieve()
                .body(Map.class);
        String outcome = (String) resp.get("outcome");
        int unitPrice = ((Number) resp.getOrDefault("unitPrice", 0)).intValue();
        return new ReserveResult(ReserveOutcome.valueOf(outcome), unitPrice);
    }

    public void release(String orderId) {
        client.post().uri("/internal/reservations/{id}/release", orderId).retrieve().toBodilessEntity();
    }

    public void confirm(String orderId) {
        client.post().uri("/internal/reservations/{id}/confirm", orderId).retrieve().toBodilessEntity();
    }
}
