package com.study.app.web;

import com.study.app.command.ProductCommandService;
import com.study.app.domain.Product;
import com.study.app.query.ProductQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.UUID;

@RestController
public class ProductController {

    @Autowired
    private ProductCommandService productCommandService;
    @Autowired
    private ProductQueryService productQueryService;

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createProduct(@RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        Object unitPriceObj = body.get("unitPrice");
        Object stockObj = body.get("stock");

        if (name == null || name.isBlank() || unitPriceObj == null || stockObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "missing required fields");
        }

        int unitPrice;
        int stock;
        try {
            unitPrice = ((Number) unitPriceObj).intValue();
            stock = ((Number) stockObj).intValue();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid field types");
        }

        if (unitPrice < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPrice must be >= 1");
        if (stock < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "stock must be >= 0");

        Product p = productCommandService.createProduct(name, unitPrice, stock);
        return productToMap(p);
    }

    @GetMapping("/products/{id}")
    public Map<String, Object> getProduct(@PathVariable String id) {
        return productToMap(productQueryService.getProduct(parseUuidOrNotFound(id)));
    }

    @PostMapping("/products/{id}/restock")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void restock(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Object unitsObj = body.get("units");
        if (unitsObj == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "units is required");
        }
        int units;
        try {
            units = ((Number) unitsObj).intValue();
        } catch (ClassCastException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid units");
        }
        if (units < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "units must be >= 1");
        productCommandService.restock(parseUuidOrNotFound(id), units);
    }

    private Map<String, Object> productToMap(Product p) {
        return Map.of(
            "id", p.getId().toString(),
            "name", p.getName(),
            "unitPrice", p.getUnitPrice(),
            "stock", p.getStock()
        );
    }

    private UUID parseUuidOrNotFound(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }
    }
}
