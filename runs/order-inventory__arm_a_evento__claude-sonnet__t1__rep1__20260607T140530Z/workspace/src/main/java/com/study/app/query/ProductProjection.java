package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetProductQuery;
import com.study.app.domain.view.ProductView;
import com.study.app.query.repository.ProductRepository;

import java.util.NoSuchElementException;

@Projection
public class ProductProjection {

    private final ProductRepository productRepository;

    public ProductProjection(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @QueryHandler
    Single<ProductView> query(GetProductQuery q) {
        return productRepository.findById(q.getProductId())
                .map(e -> Single.of(new ProductView(e.getId(), e.getName(), e.getUnitPrice(), e.getStock())))
                .orElseThrow(() -> new NoSuchElementException("product not found: " + q.getProductId()));
    }
}
