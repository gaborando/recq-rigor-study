package com.study.app.command;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.UUID;

@Component
public class InventoryClient {

    private final RestClient client;

    public InventoryClient(@Qualifier("inventoryRestClient") RestClient client) {
        this.client = client;
    }

    public record ProductInfo(UUID id, String name, int unitPrice, int stock) {}
    record ReserveRequest(UUID orderId, int quantity) {}
    record ReleaseRequest(UUID orderId, int quantity) {}

    public ProductInfo getProduct(UUID productId) {
        return client.get()
            .uri("/products/{id}", productId)
            .retrieve()
            .body(ProductInfo.class);
    }

    public boolean reserve(UUID productId, UUID orderId, int quantity) {
        var response = client.post()
            .uri("/internal/products/{productId}/reserve", productId)
            .body(new ReserveRequest(orderId, quantity))
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {})
            .toBodilessEntity();
        return response.getStatusCode().is2xxSuccessful();
    }

    public void release(UUID productId, UUID orderId, int quantity) {
        client.post()
            .uri("/internal/products/{productId}/release", productId)
            .body(new ReleaseRequest(orderId, quantity))
            .retrieve()
            .toBodilessEntity();
    }
}
