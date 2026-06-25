package com.study.app.web;

import com.study.app.command.InventoryService;
import com.study.app.command.InventoryService.ReserveResult;
import com.study.app.domain.Product;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.study.app.web.Validation.atLeast;
import static com.study.app.web.Validation.notBlank;

@RestController
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    // ---- public product API ----

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public Dtos.ProductView create(@RequestBody Dtos.CreateProduct body) {
        notBlank(body.name(), "name");
        atLeast(body.unitPrice(), 1, "unitPrice");
        atLeast(body.stock(), 0, "stock");
        return view(service.createProduct(body.name(), body.unitPrice(), body.stock()));
    }

    @GetMapping("/products/{id}")
    public Dtos.ProductView get(@PathVariable String id) {
        Product p = service.find(id);
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown product");
        return view(p);
    }

    @PostMapping("/products/{id}/restock")
    public ResponseEntity<Void> restock(@PathVariable String id, @RequestBody Dtos.Restock body) {
        atLeast(body.units(), 1, "units");
        if (!service.restock(id, body.units())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown product");
        }
        return ResponseEntity.accepted().build();
    }

    // ---- internal saga API ----

    @PostMapping("/internal/reservations")
    public Dtos.ReserveResponse reserve(@RequestBody Dtos.ReserveRequest body) {
        notBlank(body.orderId(), "orderId");
        notBlank(body.productId(), "productId");
        atLeast(body.quantity(), 1, "quantity");
        ReserveResult r = service.reserve(body.orderId(), body.productId(), body.quantity());
        return new Dtos.ReserveResponse(r.outcome().name(), r.unitPrice());
    }

    @PostMapping("/internal/reservations/{orderId}/release")
    public ResponseEntity<Void> release(@PathVariable String orderId) {
        service.release(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/internal/reservations/{orderId}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable String orderId) {
        service.confirm(orderId);
        return ResponseEntity.ok().build();
    }

    private static Dtos.ProductView view(Product p) {
        return new Dtos.ProductView(p.getId(), p.getName(), p.getUnitPrice(), p.getAvailable());
    }
}
