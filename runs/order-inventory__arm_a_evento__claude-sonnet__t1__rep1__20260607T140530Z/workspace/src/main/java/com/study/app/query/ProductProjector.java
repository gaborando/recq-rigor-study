package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ProductCreatedEvent;
import com.study.app.domain.event.StockReleasedEvent;
import com.study.app.domain.event.StockReservationFailedEvent;
import com.study.app.domain.event.StockReservedEvent;
import com.study.app.domain.event.StockRestockedEvent;
import com.study.app.query.entity.ProductEntity;
import com.study.app.query.repository.ProductRepository;

@Projector(version = 1)
public class ProductProjector {

    private final ProductRepository productRepository;

    public ProductProjector(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @EventHandler
    void on(ProductCreatedEvent e) {
        var entity = new ProductEntity(e.getProductId(), e.getName(), e.getUnitPrice(), e.getStock());
        productRepository.save(entity);
    }

    @EventHandler
    void on(StockRestockedEvent e) {
        productRepository.findById(e.getProductId()).ifPresent(p -> {
            p.setStock(p.getStock() + e.getUnits());
            productRepository.save(p);
        });
    }

    @EventHandler
    void on(StockReservedEvent e) {
        productRepository.findById(e.getProductId()).ifPresent(p -> {
            p.setStock(p.getStock() - e.getQuantity());
            productRepository.save(p);
        });
    }

    @EventHandler
    void on(StockReservationFailedEvent e) {
        // no stock change
    }

    @EventHandler
    void on(StockReleasedEvent e) {
        productRepository.findById(e.getProductId()).ifPresent(p -> {
            p.setStock(p.getStock() + e.getQuantity());
            productRepository.save(p);
        });
    }
}
