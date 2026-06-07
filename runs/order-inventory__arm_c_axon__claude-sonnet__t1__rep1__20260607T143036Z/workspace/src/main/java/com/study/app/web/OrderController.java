package com.study.app.web;

import com.study.app.command.CreateOrderCommand;
import com.study.app.query.FindOrder;
import com.study.app.query.OrderView;
import org.axonframework.commandhandling.CommandExecutionException;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStoreException;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public OrderController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public record PlaceOrderRequest(String orderId, String customerId, String productId, Integer quantity) {}

    @PostMapping
    public ResponseEntity<?> place(@RequestBody PlaceOrderRequest body) {
        if (body.quantity() == null || body.quantity() < 1) {
            return ResponseEntity.badRequest().body("quantity must be >= 1");
        }
        if (body.orderId() == null || body.customerId() == null || body.productId() == null) {
            return ResponseEntity.badRequest().body("Missing required fields");
        }

        try {
            commandGateway.sendAndWait(new CreateOrderCommand(
                    body.orderId(), body.customerId(), body.productId(), body.quantity()));
            return ResponseEntity.accepted().body(Map.of("orderId", body.orderId(), "status", "PENDING"));
        } catch (CommandExecutionException e) {
            if (isDuplicateOrConcurrency(e)) {
                // Idempotent: order already exists
                return ResponseEntity.ok(Map.of("orderId", body.orderId(), "status", "PENDING"));
            }
            // Other command failures (e.g. validation)
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            if (isDuplicateOrConcurrency(e)) {
                return ResponseEntity.ok(Map.of("orderId", body.orderId(), "status", "PENDING"));
            }
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@PathVariable String id) throws ExecutionException, InterruptedException {
        OrderView v = queryGateway.query(new FindOrder(id), ResponseTypes.instanceOf(OrderView.class)).get();
        if (v == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        Map<String, Object> resp = new HashMap<>();
        resp.put("orderId", v.getOrderId());
        resp.put("customerId", v.getCustomerId());
        resp.put("productId", v.getProductId());
        resp.put("quantity", v.getQuantity());
        resp.put("status", v.getStatus());
        if (v.getReason() != null) resp.put("reason", v.getReason());
        if (v.getTotal() != null) resp.put("total", v.getTotal());
        return ResponseEntity.ok(resp);
    }

    private boolean isDuplicateOrConcurrency(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String name = cause.getClass().getSimpleName();
            if (name.contains("Concurrency") || name.contains("Duplicate")
                    || name.contains("OptimisticLocking") || name.contains("DataIntegrity")
                    || name.contains("AggregateStreamCreation")
                    || cause instanceof EventStoreException) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
