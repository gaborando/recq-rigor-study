package com.study.app.web;

import com.study.app.command.ProductCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/products")
public class ProductInternalController {

    private final ProductCommandService commandService;

    public ProductInternalController(ProductCommandService commandService) {
        this.commandService = commandService;
    }

    record ReserveRequest(UUID orderId, int quantity) {}
    record ReleaseRequest(UUID orderId, int quantity) {}

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<Map<String,String>> reserve(@PathVariable UUID productId, @RequestBody ReserveRequest req) {
        boolean ok = commandService.reserve(productId, req.quantity());
        if (ok) {
            return ResponseEntity.ok(Map.of("status", "RESERVED"));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "OUT_OF_STOCK"));
        }
    }

    @PostMapping("/{productId}/release")
    public ResponseEntity<Void> release(@PathVariable UUID productId, @RequestBody ReleaseRequest req) {
        commandService.release(productId, req.quantity());
        return ResponseEntity.ok().build();
    }
}
