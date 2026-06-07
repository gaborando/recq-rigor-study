package com.study.app.web;

import com.evento.application.EventoBundle;
import com.evento.common.modeling.exceptions.AggregateNotInitializedError;
import com.study.app.domain.view.ProductView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@RestController
public class ProductController {

    private final ProductInvoker invoker;

    public ProductController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(ProductInvoker.class);
    }

    record CreateProductRequest(String name, Integer unitPrice, Integer stock) {}
    record RestockRequest(Integer units) {}

    @PostMapping("/products")
    public ResponseEntity<?> createProduct(@RequestBody CreateProductRequest r) {
        if (r.name() == null || r.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        if (r.unitPrice() == null || r.unitPrice() < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "unitPrice must be >= 1"));
        }
        if (r.stock() == null || r.stock() < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "stock must be >= 0"));
        }
        try {
            String productId = invoker.createProduct(r.name(), r.unitPrice(), r.stock());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", productId, "name", r.name(), "unitPrice", r.unitPrice(), "stock", r.stock()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProduct(@PathVariable String id) {
        try {
            ProductView view = invoker.getProduct(id);
            return ResponseEntity.ok(view);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/products/{id}/restock")
    public ResponseEntity<?> restockProduct(@PathVariable String id, @RequestBody RestockRequest r) {
        if (r.units() == null || r.units() < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "units must be >= 1"));
        }
        try {
            invoker.restockProduct(id, r.units());
            return ResponseEntity.accepted().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AggregateNotInitializedError || cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            }
            String name = cause == null ? "" : cause.getClass().getSimpleName();
            if (name.contains("NotInitialized")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
