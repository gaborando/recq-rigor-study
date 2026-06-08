package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.FindOrderQuery;
import com.study.app.domain.query.GetOrderStatsQuery;
import com.study.app.domain.view.OrderStatsView;
import com.study.app.domain.view.OrderView;

import java.util.NoSuchElementException;

@Projection
public class OrderProjection {

    private final OrderRepository repository;

    public OrderProjection(OrderRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<OrderView> query(FindOrderQuery q) {
        OrderEntity entity = repository.findById(q.getOrderId())
                .orElseThrow(() -> new NoSuchElementException("order not found: " + q.getOrderId()));
        return Single.of(new OrderView(
                entity.getOrderId(), entity.getCustomerId(), entity.getProductId(),
                entity.getQuantity(), entity.getStatus(), entity.getReason(), entity.getTotal()));
    }

    @QueryHandler
    Single<OrderStatsView> query(GetOrderStatsQuery q) {
        return Single.of(new OrderStatsView(
                OrderStatsStore.getConfirmed(),
                OrderStatsStore.getRejected(),
                OrderStatsStore.getRevenue()));
    }
}
