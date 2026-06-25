package com.study.app.web;

import com.study.app.command.CreateProductCommand;
import com.study.app.command.RestockCommand;
import com.study.app.query.FindProduct;
import com.study.app.query.ProductDto;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public ProductController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public record CreateProductRequest(String name, Integer unitPrice, Integer stock) {}
    public record RestockRequest(Integer units) {}

    @PostMapping
    public ResponseEntity<ProductDto> create(@RequestBody CreateProductRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()
                || req.unitPrice() == null || req.unitPrice() < 1
                || req.stock() == null || req.stock() < 0) {
            throw new IllegalArgumentException("invalid product");
        }
        String id = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new CreateProductCommand(id, req.name(), req.unitPrice(), req.stock()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ProductDto(id, req.name(), req.unitPrice(), req.stock()));
    }

    @PostMapping("/{id}/restock")
    public ResponseEntity<Void> restock(@PathVariable String id, @RequestBody RestockRequest req) {
        if (req == null || req.units() == null || req.units() < 1) {
            throw new IllegalArgumentException("invalid units");
        }
        try {
            commandGateway.sendAndWait(new RestockCommand(id, req.units()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    public ProductDto get(@PathVariable String id) {
        ProductDto dto = queryGateway.query(new FindProduct(id), ResponseTypes.instanceOf(ProductDto.class)).join();
        if (dto == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return dto;
    }
}
