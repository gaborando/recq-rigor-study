package com.study.app.web;

import com.study.app.command.CreateProductCommand;
import com.study.app.command.RestockProductCommand;
import com.study.app.query.FindProduct;
import com.study.app.query.ProductView;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
public class ProductController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public ProductController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public record CreateProductRequest(String name, Integer unitPrice, Integer stock) {}
    public record RestockRequest(Integer units) {}

    @PostMapping("/products")
    public ResponseEntity<?> create(@RequestBody CreateProductRequest body) {
        if (body.name() == null || body.name().isBlank()
                || body.unitPrice() == null || body.unitPrice() < 1
                || body.stock() == null || body.stock() < 0) {
            return ResponseEntity.badRequest().body("Invalid product fields");
        }
        String productId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new CreateProductCommand(productId, body.name(), body.unitPrice(), body.stock()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", productId, "name", body.name(), "unitPrice", body.unitPrice(), "stock", body.stock()));
    }

    @GetMapping("/products/{id}")
    public ProductView get(@PathVariable String id) throws ExecutionException, InterruptedException {
        ProductView v = queryGateway.query(new FindProduct(id), ResponseTypes.instanceOf(ProductView.class)).get();
        if (v == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return v;
    }

    @PostMapping("/products/{id}/restock")
    public ResponseEntity<?> restock(@PathVariable String id, @RequestBody RestockRequest body) {
        if (body.units() == null || body.units() < 1) {
            return ResponseEntity.badRequest().body("units must be >= 1");
        }
        // verify product exists
        try {
            ProductView v = queryGateway.query(new FindProduct(id), ResponseTypes.instanceOf(ProductView.class)).get();
            if (v == null) {
                // might not be in view yet; send command and let aggregate validate
            }
        } catch (Exception ignored) {}

        try {
            commandGateway.send(new RestockProductCommand(id, body.units()));
        } catch (Exception e) {
            if (isNotFound(e)) return ResponseEntity.notFound().build();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return ResponseEntity.accepted().build();
    }

    private boolean isNotFound(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.axonframework.modelling.command.AggregateNotFoundException) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
