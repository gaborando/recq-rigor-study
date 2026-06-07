package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.ProductView;

public class GetProductQuery extends Query<Single<ProductView>> {
    private String productId;

    public GetProductQuery() {}

    public GetProductQuery(String productId) {
        this.productId = productId;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}
