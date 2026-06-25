package com.study.app.web;

import com.study.app.command.CreateOrderCommand;
import com.study.app.query.FindOrder;
import com.study.app.query.OrderDto;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final CommandGateway plainGateway;
    private final QueryGateway queryGateway;

    public OrderController(@Qualifier("plainCommandGateway") CommandGateway plainGateway,
                           QueryGateway queryGateway) {
        this.plainGateway = plainGateway;
        this.queryGateway = queryGateway;
    }

    public record CreateOrderRequest(String orderId, String customerId, String productId, Integer quantity) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateOrderRequest req) {
        if (req == null || req.orderId() == null || req.orderId().isBlank()
                || req.customerId() == null || req.customerId().isBlank()
                || req.productId() == null || req.productId().isBlank()
                || req.quantity() == null || req.quantity() < 1) {
            throw new IllegalArgumentException("invalid order");
        }
        // Idempotent on orderId: a duplicate create conflicts at the event store
        // (sequence 0 already exists) and is harmlessly dropped. Fire-and-forget.
        plainGateway.send(new CreateOrderCommand(
                req.orderId(), req.customerId(), req.productId(), req.quantity()));
        return ResponseEntity.accepted()
                .body(Map.of("orderId", req.orderId(), "status", "PENDING"));
    }

    @GetMapping("/{id}")
    public OrderDto get(@PathVariable String id) {
        OrderDto dto = queryGateway.query(new FindOrder(id), ResponseTypes.instanceOf(OrderDto.class)).join();
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return dto;
    }
}
