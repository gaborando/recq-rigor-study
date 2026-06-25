package com.study.app.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public REST contract. The edge owns no state; it forwards writes to the
 * owning service (propagating the downstream status) and composes reads that
 * span services (list view = lists summary + items; stats = lists + items).
 */
@RestController
public class EdgeController {

    private final RestClient lists;
    private final RestClient items;
    private final RestClient notifications;

    public EdgeController(@Qualifier("listsClient") RestClient lists,
                          @Qualifier("itemsClient") RestClient items,
                          @Qualifier("notificationsClient") RestClient notifications) {
        this.lists = lists;
        this.items = items;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- writes

    @PostMapping("/lists")
    public ResponseEntity<byte[]> createList(@RequestBody(required = false) byte[] body) {
        return proxy(lists, HttpMethod.POST, "/lists", body);
    }

    @PostMapping("/lists/{id}/items")
    public ResponseEntity<byte[]> addItem(@PathVariable String id,
                                          @RequestBody(required = false) byte[] body) {
        return proxy(items, HttpMethod.POST, "/lists/" + id + "/items", body);
    }

    @PutMapping("/lists/{id}/items/{itemId}/check")
    public ResponseEntity<byte[]> check(@PathVariable String id, @PathVariable String itemId) {
        return proxy(items, HttpMethod.PUT, "/lists/" + id + "/items/" + itemId + "/check", null);
    }

    @PutMapping("/lists/{id}/items/{itemId}/uncheck")
    public ResponseEntity<byte[]> uncheck(@PathVariable String id, @PathVariable String itemId) {
        return proxy(items, HttpMethod.PUT, "/lists/" + id + "/items/" + itemId + "/uncheck", null);
    }

    @PutMapping("/lists/{id}/items/{itemId}/rename")
    public ResponseEntity<byte[]> rename(@PathVariable String id, @PathVariable String itemId,
                                         @RequestBody(required = false) byte[] body) {
        return proxy(items, HttpMethod.PUT, "/lists/" + id + "/items/" + itemId + "/rename", body);
    }

    // ---------------------------------------------------------------- reads

    @GetMapping("/lists/{id}")
    public ResponseEntity<?> getList(@PathVariable String id) {
        Map<?, ?> summary;
        try {
            summary = lists.get().uri("/lists/{id}", id).retrieve().body(Map.class);
        } catch (HttpClientErrorException.NotFound nf) {
            return ResponseEntity.notFound().build();
        }
        if (summary == null) {
            return ResponseEntity.notFound().build();
        }
        List<?> itemList = items.get().uri("/lists/{id}/items", id).retrieve().body(List.class);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("listId", summary.get("listId"));
        view.put("name", summary.get("name"));
        view.put("status", summary.get("status"));
        view.put("items", itemList != null ? itemList : List.of());
        return ResponseEntity.ok(view);
    }

    @GetMapping("/lists/{id}/notifications")
    public ResponseEntity<byte[]> getNotifications(@PathVariable String id) {
        return proxy(notifications, HttpMethod.GET, "/lists/" + id + "/notifications", null);
    }

    @GetMapping("/stats/lists")
    public Map<String, Object> stats() {
        Map<?, ?> listsStats = lists.get().uri("/stats").retrieve().body(Map.class);
        Map<?, ?> itemsStats = items.get().uri("/stats").retrieve().body(Map.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", listsStats.get("active"));
        out.put("completed", listsStats.get("completed"));
        out.put("totalItems", itemsStats.get("totalItems"));
        out.put("checkedItems", itemsStats.get("checkedItems"));
        return out;
    }

    // ---------------------------------------------------------------- helper

    private ResponseEntity<byte[]> proxy(RestClient client, HttpMethod method, String uri, byte[] body) {
        RestClient.RequestBodySpec spec = client.method(method).uri(uri);
        if (body != null && body.length > 0) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((request, response) -> {
            byte[] respBody = response.getBody().readAllBytes();
            ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
            MediaType ct = response.getHeaders().getContentType();
            if (ct != null) {
                builder = builder.contentType(ct);
            }
            return builder.body(respBody);
        });
    }
}
