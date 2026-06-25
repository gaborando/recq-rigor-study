package com.study.app.command;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Thin HTTP gateway to the flights and payments services. Every call targets an
 * idempotent endpoint keyed by bookingId, so the bounded retry here is safe.
 */
@Component
public class PeerGateway {

    private final RestClient flights;
    private final RestClient payments;

    public PeerGateway(RestClient flightsClient, RestClient paymentsClient) {
        this.flights = flightsClient;
        this.payments = paymentsClient;
    }

    public Map<String, Object> hold(String flightId, String seat, String bookingId) {
        return post(flights, "/internal/hold",
                Map.of("flightId", flightId, "seat", seat, "bookingId", bookingId));
    }

    public void release(String flightId, String seat, String bookingId) {
        post(flights, "/internal/release",
                Map.of("flightId", flightId, "seat", seat, "bookingId", bookingId));
    }

    public boolean confirm(String flightId, String seat, String bookingId) {
        Map<String, Object> r = post(flights, "/internal/confirm",
                Map.of("flightId", flightId, "seat", seat, "bookingId", bookingId));
        return Boolean.TRUE.equals(r.get("confirmed"));
    }

    public String charge(String bookingId, String customerId, long amount) {
        Map<String, Object> r = post(payments, "/internal/charge",
                Map.of("bookingId", bookingId, "customerId", customerId, "amount", amount));
        return (String) r.get("result");
    }

    public void refund(String bookingId) {
        post(payments, "/internal/refund", Map.of("bookingId", bookingId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(RestClient client, String path, Map<String, Object> body) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                return client.post().uri(path).body(body).retrieve().body(Map.class);
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(200L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }
}
