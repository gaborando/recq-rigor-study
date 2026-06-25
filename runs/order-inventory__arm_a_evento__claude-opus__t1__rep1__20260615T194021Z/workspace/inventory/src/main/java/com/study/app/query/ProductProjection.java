package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.ProductByIdQuery;
import com.study.app.domain.view.ProductView;

import java.util.NoSuchElementException;

@Projection
public class ProductProjection {

    private final ProductViewRepository repository;

    public ProductProjection(ProductViewRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<ProductView> query(ProductByIdQuery q) {
        var e = repository.findById(q.getProductId())
                .orElseThrow(() -> new NoSuchElementException("product not found: " + q.getProductId()));
        return Single.of(new ProductView(e.getId(), e.getName(), e.getUnitPrice(), e.getStock()));
    }
}
