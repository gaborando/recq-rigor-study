package com.study.app.web;

import com.study.app.command.CustomerCommandService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/customers")
public class CustomerInternalController {

    private final CustomerCommandService commandService;

    public CustomerInternalController(CustomerCommandService commandService) {
        this.commandService = commandService;
    }

    record ChargeRequest(UUID orderId, int amount) {}
    record NotifyRequest(UUID orderId, String status, String reason) {}

    @PostMapping("/{customerId}/charge")
    public ResponseEntity<Map<String,String>> charge(@PathVariable UUID customerId, @RequestBody ChargeRequest req) {
        boolean ok = commandService.charge(customerId, req.amount());
        if (ok) {
            return ResponseEntity.ok(Map.of("status", "CHARGED"));
        } else {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", "INSUFFICIENT_FUNDS"));
        }
    }

    @PostMapping("/{customerId}/notify")
    public ResponseEntity<Void> notify(@PathVariable UUID customerId, @RequestBody NotifyRequest req) {
        commandService.notify(customerId, req.orderId(), req.status(), req.reason());
        return ResponseEntity.ok().build();
    }
}
