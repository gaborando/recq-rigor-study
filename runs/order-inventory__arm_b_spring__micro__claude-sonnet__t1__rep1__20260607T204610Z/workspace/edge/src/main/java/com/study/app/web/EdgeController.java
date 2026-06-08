package com.study.app.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

@RestController
public class EdgeController {

    private final RestClient ordersClient;
    private final RestClient inventoryClient;
    private final RestClient customersClient;

    public EdgeController(
            @Qualifier("ordersClient") RestClient ordersClient,
            @Qualifier("inventoryClient") RestClient inventoryClient,
            @Qualifier("customersClient") RestClient customersClient) {
        this.ordersClient = ordersClient;
        this.inventoryClient = inventoryClient;
        this.customersClient = customersClient;
    }

    // ---- Products (inventory) ----

    @PostMapping("/products")
    public ResponseEntity<String> createProduct(@RequestBody String body) {
        return proxyPost(inventoryClient, "/products", body);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<String> getProduct(@PathVariable String id) {
        return proxyGet(inventoryClient, "/products/" + id);
    }

    @PostMapping("/products/{id}/restock")
    public ResponseEntity<String> restockProduct(@PathVariable String id, @RequestBody String body) {
        return proxyPost(inventoryClient, "/products/" + id + "/restock", body);
    }

    // ---- Customers ----

    @PostMapping("/customers")
    public ResponseEntity<String> createCustomer(@RequestBody String body) {
        return proxyPost(customersClient, "/customers", body);
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<String> getCustomer(@PathVariable String id) {
        return proxyGet(customersClient, "/customers/" + id);
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<String> deposit(@PathVariable String id, @RequestBody String body) {
        return proxyPost(customersClient, "/customers/" + id + "/deposit", body);
    }

    @GetMapping("/customers/{id}/notifications")
    public ResponseEntity<String> notifications(@PathVariable String id) {
        return proxyGet(customersClient, "/customers/" + id + "/notifications");
    }

    // ---- Orders ----

    @PostMapping("/orders")
    public ResponseEntity<String> placeOrder(@RequestBody String body) {
        return proxyPost(ordersClient, "/orders", body);
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<String> getOrder(@PathVariable String id) {
        return proxyGet(ordersClient, "/orders/" + id);
    }

    // ---- Stats ----

    @GetMapping("/stats/orders")
    public ResponseEntity<String> statsOrders() {
        return proxyGet(ordersClient, "/stats/orders");
    }

    // ---- Helpers ----

    private ResponseEntity<String> proxyGet(RestClient client, String path) {
        return proxy(client.get().uri(path).retrieve());
    }

    private ResponseEntity<String> proxyPost(RestClient client, String path, String body) {
        return proxy(client.post().uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve());
    }

    private ResponseEntity<String> proxy(RestClient.ResponseSpec spec) {
        try {
            ResponseEntity<String> upstream = spec
                .onStatus(HttpStatusCode::isError, (req, resp) -> {})
                .toEntity(String.class);
            // Return status + body only; let Tomcat own Transfer-Encoding
            return ResponseEntity.status(upstream.getStatusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(upstream.getBody());
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"upstream unavailable\"}");
        }
    }
}
