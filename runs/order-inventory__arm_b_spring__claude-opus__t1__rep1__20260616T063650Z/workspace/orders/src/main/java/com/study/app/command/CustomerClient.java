package com.study.app.command;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * REST client for the customers service. Charge and notify are keyed by orderId on
 * the customers side, so retries produce no extra debit and no duplicate notification.
 */
@Component
public class CustomerClient {

    public enum ChargeOutcome { CHARGED, INSUFFICIENT_FUNDS, UNKNOWN_CUSTOMER }

    private final RestClient client;

    public CustomerClient(RestClient customersRestClient) {
        this.client = customersRestClient;
    }

    public ChargeOutcome charge(String orderId, String customerId, long amount) {
        Map<String, Object> resp = client.post()
                .uri("/internal/charges")
                .body(Map.of("orderId", orderId, "customerId", customerId, "amount", amount))
                .retrieve()
                .body(Map.class);
        return ChargeOutcome.valueOf((String) resp.get("outcome"));
    }

    public void notify(String orderId, String customerId, String status, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId);
        body.put("customerId", customerId);
        body.put("status", status);
        body.put("reason", reason);
        client.post().uri("/internal/notifications").body(body).retrieve().toBodilessEntity();
    }
}
