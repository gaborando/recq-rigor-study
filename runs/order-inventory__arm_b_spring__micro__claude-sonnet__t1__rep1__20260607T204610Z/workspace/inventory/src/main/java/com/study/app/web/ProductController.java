package com.study.app.web;

import com.study.app.command.ProductCommandService;
import com.study.app.domain.Product;
import com.study.app.domain.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
public class ProductController {

    private final ProductCommandService commandService;
    private final ProductRepository products;

    public ProductController(ProductCommandService commandService, ProductRepository products) {
        this.commandService = commandService;
        this.products = products;
    }

    record CreateProductRequest(
        @NotBlank String name,
        @NotNull @Min(1) Integer unitPrice,
        @NotNull @Min(0) Integer stock
    ) {}

    record RestockRequest(@NotNull @Min(1) Integer units) {}

    record ProductView(UUID id, String name, int unitPrice, int stock) {}

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@RequestBody @Valid CreateProductRequest req) {
        Product p = commandService.create(req.name(), req.unitPrice(), req.stock());
        return new ProductView(p.getId(), p.getName(), p.getUnitPrice(), p.getStock());
    }

    @GetMapping("/products/{id}")
    public ProductView get(@PathVariable String id) {
        UUID uid = parseUuid(id);
        return products.findById(uid)
            .map(p -> new ProductView(p.getId(), p.getName(), p.getUnitPrice(), p.getStock()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
    }

    @PostMapping("/products/{id}/restock")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void restock(@PathVariable String id, @RequestBody @Valid RestockRequest req) {
        commandService.restock(parseUuid(id), req.units());
    }

    private UUID parseUuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found");
        }
    }
}
