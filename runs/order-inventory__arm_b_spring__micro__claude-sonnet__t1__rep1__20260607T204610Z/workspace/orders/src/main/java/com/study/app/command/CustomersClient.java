package com.study.app.command;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.UUID;

@Component
public class CustomersClient {

    private final RestClient client;

    public CustomersClient(@Qualifier("customersRestClient") RestClient client) {
        this.client = client;
    }

    record ChargeRequest(UUID orderId, int amount) {}
    record NotifyRequest(UUID orderId, String status, String reason) {}

    public boolean charge(UUID customerId, UUID orderId, int amount) {
        var response = client.post()
            .uri("/internal/customers/{customerId}/charge", customerId)
            .body(new ChargeRequest(orderId, amount))
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, resp) -> {})
            .toBodilessEntity();
        return response.getStatusCode().is2xxSuccessful();
    }

    public void notify(UUID customerId, UUID orderId, String status, String reason) {
        try {
            client.post()
                .uri("/internal/customers/{customerId}/notify", customerId)
                .body(new NotifyRequest(orderId, status, reason))
                .retrieve()
                .toBodilessEntity();
        } catch (Exception ignored) {}
    }
}
