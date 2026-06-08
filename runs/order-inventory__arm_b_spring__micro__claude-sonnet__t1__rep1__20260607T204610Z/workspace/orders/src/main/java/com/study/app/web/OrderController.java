package com.study.app.web;

import com.study.app.command.OrderCommandService;
import com.study.app.domain.Order;
import com.study.app.domain.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class OrderController {

    private final OrderCommandService commandService;
    private final OrderRepository orders;

    public OrderController(OrderCommandService commandService, OrderRepository orders) {
        this.commandService = commandService;
        this.orders = orders;
    }

    record PlaceOrderRequest(
        @NotNull UUID orderId,
        @NotNull UUID customerId,
        @NotNull UUID productId,
        @NotNull @Min(1) Integer quantity
    ) {}

    record OrderView(UUID orderId, UUID customerId, UUID productId, int quantity, String status, String reason, Integer total) {}

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderView place(@RequestBody @Valid PlaceOrderRequest req) {
        Order o = commandService.create(req.orderId(), req.customerId(), req.productId(), req.quantity());
        return toView(o);
    }

    @GetMapping("/orders/{id}")
    public OrderView get(@PathVariable UUID id) {
        return orders.findById(id)
            .map(this::toView)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }

    private OrderView toView(Order o) {
        return new OrderView(o.getId(), o.getCustomerId(), o.getProductId(), o.getQuantity(), o.getStatus(), o.getReason(), o.getTotal());
    }
}
