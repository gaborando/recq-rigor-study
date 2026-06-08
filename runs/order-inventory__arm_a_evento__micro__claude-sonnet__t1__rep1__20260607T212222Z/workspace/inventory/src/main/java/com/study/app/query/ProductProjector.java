package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ProductCreatedEvent;
import com.study.app.domain.event.ProductRestockedEvent;
import com.study.app.domain.event.StockReleasedEvent;
import com.study.app.domain.event.StockReservedEvent;

@Projector(version = 1)
public class ProductProjector {

    private final ProductRepository repository;

    public ProductProjector(ProductRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void on(ProductCreatedEvent e) {
        repository.save(new ProductEntity(e.getProductId(), e.getName(), e.getUnitPrice(), e.getStock()));
    }

    @EventHandler
    void on(ProductRestockedEvent e) {
        repository.findById(e.getProductId()).ifPresent(p -> {
            p.setStock(p.getStock() + e.getUnits());
            repository.save(p);
        });
    }

    @EventHandler
    void on(StockReservedEvent e) {
        repository.findById(e.getProductId()).ifPresent(p -> {
            p.setStock(p.getStock() - e.getQuantity());
            repository.save(p);
        });
    }

    @EventHandler
    void on(StockReleasedEvent e) {
        repository.findById(e.getProductId()).ifPresent(p -> {
            p.setStock(p.getStock() + e.getQuantity());
            repository.save(p);
        });
    }
}
