package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ProductCreatedEvent;
import com.study.app.domain.event.ProductRestockedEvent;
import com.study.app.domain.event.StockReleasedEvent;
import com.study.app.domain.event.StockReservedEvent;

/**
 * Builds the product read model. Single active consumer + ordered delivery means
 * ProductCreated is always applied before the stock adjustments that follow it.
 */
@Projector(version = 1)
public class ProductProjector {

    private final ProductViewRepository repository;

    public ProductProjector(ProductViewRepository repository) {
        this.repository = repository;
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(ProductCreatedEvent e) {
        repository.save(new ProductViewEntity(e.getProductId(), e.getName(), e.getUnitPrice(), e.getStock()));
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(ProductRestockedEvent e) {
        repository.adjustStock(e.getProductId(), e.getUnits());
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(StockReservedEvent e) {
        repository.adjustStock(e.getProductId(), -e.getQuantity());
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(StockReleasedEvent e) {
        repository.adjustStock(e.getProductId(), e.getQuantity());
    }
}
