package com.study.app.web;

import com.study.app.command.OrderCommandService;
import com.study.app.domain.Order;
import com.study.app.query.OrderQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController
public class OrderController {

    @Autowired
    private OrderCommandService orderCommandService;
    @Autowired
    private OrderQueryService orderQueryService;

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> placeOrder(@RequestBody Map<String, Object> body) {
        Object orderIdObj = body.get("orderId");
        Object customerIdObj = body.get("customerId");
        Object productIdObj = body.get("productId");
        Object quantityObj = body.get("quantity");

        if (orderIdObj == null || customerIdObj == null || productIdObj == null || quantityObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required fields");
        }

        int quantity;
        try {
            quantity = ((Number) quantityObj).intValue();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid quantity");
        }
        if (quantity < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be >= 1");

        UUID orderId, customerId, productId;
        try {
            orderId = UUID.fromString(orderIdObj.toString());
            customerId = UUID.fromString(customerIdObj.toString());
            productId = UUID.fromString(productIdObj.toString());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid UUID format");
        }

        Order order = orderCommandService.placeOrder(orderId, customerId, productId, quantity);
        return orderToMap(order);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrder(@PathVariable UUID id) {
        return orderToMap(orderQueryService.getOrder(id));
    }

    private Map<String, Object> orderToMap(Order o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderId", o.getOrderId().toString());
        m.put("customerId", o.getCustomerId().toString());
        m.put("productId", o.getProductId().toString());
        m.put("quantity", o.getQuantity());
        m.put("status", o.getStatus().name());
        if (o.getReason() != null) {
            m.put("reason", o.getReason());
        }
        if (o.getTotal() != null) {
            m.put("total", o.getTotal());
        }
        return m;
    }
}
