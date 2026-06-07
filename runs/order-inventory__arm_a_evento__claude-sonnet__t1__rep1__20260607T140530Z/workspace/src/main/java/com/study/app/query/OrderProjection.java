package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetOrderQuery;
import com.study.app.domain.view.OrderView;
import com.study.app.query.repository.OrderRepository;

import java.util.NoSuchElementException;

@Projection
public class OrderProjection {

    private final OrderRepository orderRepository;

    public OrderProjection(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @QueryHandler
    Single<OrderView> query(GetOrderQuery q) {
        return orderRepository.findById(q.getOrderId())
                .map(e -> {
                    var v = new OrderView();
                    v.setOrderId(e.getOrderId());
                    v.setCustomerId(e.getCustomerId());
                    v.setProductId(e.getProductId());
                    v.setQuantity(e.getQuantity());
                    v.setStatus(e.getStatus());
                    v.setReason(e.getReason());
                    v.setTotal(e.getTotal());
                    return Single.of(v);
                })
                .orElseThrow(() -> new NoSuchElementException("order not found: " + q.getOrderId()));
    }
}
