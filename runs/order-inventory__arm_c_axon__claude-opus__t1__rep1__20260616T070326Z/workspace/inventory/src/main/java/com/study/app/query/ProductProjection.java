package com.study.app.query;

import com.study.app.domain.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("product-projection")
public class ProductProjection {

    private final ProductViewRepository products;

    public ProductProjection(ProductViewRepository products) {
        this.products = products;
    }

    @EventHandler
    public void on(ProductCreatedEvent e) {
        products.save(new ProductView(e.productId(), e.name(), e.unitPrice(), e.stock()));
    }

    @EventHandler
    public void on(StockRestockedEvent e) {
        products.findById(e.productId()).ifPresent(p -> {
            p.setStock(p.getStock() + e.units());
            products.save(p);
        });
    }

    @EventHandler
    public void on(StockReservedEvent e) {
        products.findById(e.productId()).ifPresent(p -> {
            p.setStock(p.getStock() - e.quantity());
            products.save(p);
        });
    }

    @EventHandler
    public void on(StockReleasedEvent e) {
        products.findById(e.productId()).ifPresent(p -> {
            p.setStock(p.getStock() + e.quantity());
            products.save(p);
        });
    }

    @QueryHandler
    public ProductDto handle(com.study.app.query.FindProduct q) {
        return products.findById(q.productId())
                .map(p -> new ProductDto(p.getId(), p.getName(), p.getUnitPrice(), p.getStock()))
                .orElse(null);
    }
}
