package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.ProductView;

public class ProductByIdQuery extends Query<Single<ProductView>> {
    private String productId;

    public ProductByIdQuery() {}
    public ProductByIdQuery(String productId) { this.productId = productId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}
