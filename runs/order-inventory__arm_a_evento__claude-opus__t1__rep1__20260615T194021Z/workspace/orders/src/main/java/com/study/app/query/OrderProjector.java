package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderCreatedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

/**
 * Builds the order read model. Ordered single-active consumption means the
 * PENDING row exists before its decision is applied, so status never regresses.
 */
@Projector(version = 1)
public class OrderProjector {

    private final OrderViewRepository repository;

    public OrderProjector(OrderViewRepository repository) {
        this.repository = repository;
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderCreatedEvent e) {
        repository.save(new OrderViewEntity(e.getOrderId(), e.getCustomerId(), e.getProductId(),
                e.getQuantity(), "PENDING"));
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderConfirmedEvent e) {
        var v = repository.findById(e.getOrderId()).orElse(null);
        if (v == null) return;
        v.setStatus("CONFIRMED");
        v.setTotal(e.getTotal());
        v.setReason(null);
        repository.save(v);
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderRejectedEvent e) {
        var v = repository.findById(e.getOrderId()).orElse(null);
        if (v == null) return;
        v.setStatus("REJECTED");
        v.setReason(e.getReason());
        repository.save(v);
    }
}
