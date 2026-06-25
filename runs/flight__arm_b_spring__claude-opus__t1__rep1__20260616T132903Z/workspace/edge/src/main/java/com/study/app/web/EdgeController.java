package com.study.app.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * The public API gateway. It owns no data; it forwards each route to the service
 * that owns it (flights / payments / bookings) and relays the status and body
 * verbatim. Note /customers/{id}/notifications is served by bookings while the
 * other /customers routes are served by payments.
 */
@RestController
public class EdgeController {

    private final RestClient flights;
    private final RestClient bookings;
    private final RestClient payments;

    public EdgeController(RestClient flightsClient, RestClient bookingsClient, RestClient paymentsClient) {
        this.flights = flightsClient;
        this.bookings = bookingsClient;
        this.payments = paymentsClient;
    }

    // ---- flights ----
    @PostMapping("/flights")
    public ResponseEntity<String> createFlight(@RequestBody(required = false) String body) {
        return forwardPost(flights, "/flights", body);
    }

    @GetMapping("/flights/{id}")
    public ResponseEntity<String> getFlight(@PathVariable String id) {
        return forwardGet(flights, "/flights/" + id);
    }

    // ---- customers (payments) ----
    @PostMapping("/customers")
    public ResponseEntity<String> createCustomer(@RequestBody(required = false) String body) {
        return forwardPost(payments, "/customers", body);
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<String> getCustomer(@PathVariable String id) {
        return forwardGet(payments, "/customers/" + id);
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<String> deposit(@PathVariable String id,
                                          @RequestBody(required = false) String body) {
        return forwardPost(payments, "/customers/" + id + "/deposit", body);
    }

    // ---- notifications (bookings) ----
    @GetMapping("/customers/{id}/notifications")
    public ResponseEntity<String> notifications(@PathVariable String id) {
        return forwardGet(bookings, "/customers/" + id + "/notifications");
    }

    // ---- bookings ----
    @PostMapping("/bookings")
    public ResponseEntity<String> createBooking(@RequestBody(required = false) String body) {
        return forwardPost(bookings, "/bookings", body);
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<String> getBooking(@PathVariable String id) {
        return forwardGet(bookings, "/bookings/" + id);
    }

    @GetMapping("/stats/bookings")
    public ResponseEntity<String> stats() {
        return forwardGet(bookings, "/stats/bookings");
    }

    // ---- forwarding helpers ----
    private ResponseEntity<String> forwardGet(RestClient client, String path) {
        return client.get().uri(path)
                .exchange((req, res) -> ResponseEntity
                        .status(res.getStatusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(readBody(res)));
    }

    private ResponseEntity<String> forwardPost(RestClient client, String path, String body) {
        var spec = client.post().uri(path).contentType(MediaType.APPLICATION_JSON);
        if (body != null && !body.isBlank()) {
            spec = spec.body(body);
        }
        return spec.exchange((req, res) -> ResponseEntity
                .status(res.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(readBody(res)));
    }

    private static String readBody(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse res) {
        try {
            byte[] bytes = res.getBody().readAllBytes();
            return bytes.length == 0 ? "" : new String(bytes);
        } catch (Exception e) {
            return "";
        }
    }
}
