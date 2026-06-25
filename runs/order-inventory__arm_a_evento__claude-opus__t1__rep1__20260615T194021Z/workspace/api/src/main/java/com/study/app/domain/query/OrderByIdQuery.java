package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.OrderView;

public class OrderByIdQuery extends Query<Single<OrderView>> {
    private String orderId;

    public OrderByIdQuery() {}
    public OrderByIdQuery(String orderId) { this.orderId = orderId; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}
