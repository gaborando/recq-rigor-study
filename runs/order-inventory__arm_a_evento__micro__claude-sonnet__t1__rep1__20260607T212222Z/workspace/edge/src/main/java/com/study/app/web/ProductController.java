package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.ProductView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class ProductController {

    private final ProductInvoker invoker;

    public ProductController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(ProductInvoker.class);
    }

    record CreateProductRequest(String name, Integer unitPrice, Integer stock) {}

    record RestockRequest(Integer units) {}

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody CreateProductRequest req) throws Exception {
        if (req.name() == null || req.name().isBlank())
            return ResponseEntity.badRequest().body(Map.of("error", "name required"));
        if (req.unitPrice() == null || req.unitPrice() < 1)
            return ResponseEntity.badRequest().body(Map.of("error", "unitPrice must be >= 1"));
        if (req.stock() == null || req.stock() < 0)
            return ResponseEntity.badRequest().body(Map.of("error", "stock must be >= 0"));
        ProductView view = invoker.createProduct(req.name(), req.unitPrice(), req.stock());
        return ResponseEntity.status(201).body(view);
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable String id) throws Exception {
        ProductView view = invoker.getProduct(id);
        return ResponseEntity.ok(view);
    }

    @PostMapping("/products/{id}/restock")
    public ResponseEntity<?> restock(@PathVariable String id, @RequestBody RestockRequest req) throws Exception {
        if (req.units() == null || req.units() < 1)
            return ResponseEntity.badRequest().body(Map.of("error", "units must be >= 1"));
        invoker.restock(id, req.units());
        return ResponseEntity.status(202).build();
    }
}
