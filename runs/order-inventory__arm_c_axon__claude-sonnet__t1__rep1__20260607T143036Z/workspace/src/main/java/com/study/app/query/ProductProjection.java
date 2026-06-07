package com.study.app.query;

import com.study.app.domain.events.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("product-projection")
public class ProductProjection {

    private final ProductViewRepository repo;

    public ProductProjection(ProductViewRepository repo) {
        this.repo = repo;
    }

    @EventHandler
    public void on(ProductCreatedEvent e) {
        repo.save(new ProductView(e.productId(), e.name(), e.unitPrice(), e.stock()));
    }

    @EventHandler
    public void on(ProductRestockedEvent e) {
        repo.findById(e.productId()).ifPresent(v -> {
            v.setStock(v.getStock() + e.units());
            repo.save(v);
        });
    }

    @EventHandler
    public void on(StockReservedEvent e) {
        repo.findById(e.productId()).ifPresent(v -> {
            v.setStock(v.getStock() - e.quantity());
            repo.save(v);
        });
    }

    @EventHandler
    public void on(StockReleasedEvent e) {
        repo.findById(e.productId()).ifPresent(v -> {
            v.setStock(v.getStock() + e.quantity());
            repo.save(v);
        });
    }

    @QueryHandler
    public ProductView handle(FindProduct q) {
        return repo.findById(q.productId()).orElse(null);
    }
}
