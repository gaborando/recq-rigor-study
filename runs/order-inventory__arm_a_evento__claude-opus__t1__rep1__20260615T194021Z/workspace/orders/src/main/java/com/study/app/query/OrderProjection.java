package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.OrderByIdQuery;
import com.study.app.domain.view.OrderView;

import java.util.NoSuchElementException;

@Projection
public class OrderProjection {

    private final OrderViewRepository repository;

    public OrderProjection(OrderViewRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<OrderView> query(OrderByIdQuery q) {
        var e = repository.findById(q.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("order not found: " + q.getOrderId()));
        return Single.of(new OrderView(e.getOrderId(), e.getCustomerId(), e.getProductId(),
                e.getQuantity(), e.getStatus(), e.getReason(), e.getTotal()));
    }
}
