package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.FindProductQuery;
import com.study.app.domain.view.ProductView;

import java.util.NoSuchElementException;

@Projection
public class ProductProjection {

    private final ProductRepository repository;

    public ProductProjection(ProductRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<ProductView> query(FindProductQuery q) {
        ProductEntity entity = repository.findById(q.getProductId())
                .orElseThrow(() -> new NoSuchElementException("product not found: " + q.getProductId()));
        return Single.of(new ProductView(entity.getId(), entity.getName(), entity.getUnitPrice(), entity.getStock()));
    }
}
