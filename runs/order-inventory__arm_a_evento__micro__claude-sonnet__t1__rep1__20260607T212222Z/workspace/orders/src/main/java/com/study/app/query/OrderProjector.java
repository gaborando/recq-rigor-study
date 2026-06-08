package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderPlacedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

@Projector(version = 1)
public class OrderProjector {

    private final OrderRepository repository;

    public OrderProjector(OrderRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void on(OrderPlacedEvent e) {
        if (!repository.existsById(e.getOrderId())) {
            repository.save(new OrderEntity(
                    e.getOrderId(), e.getCustomerId(), e.getProductId(), e.getQuantity(), "PENDING"));
        }
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        repository.findById(e.getOrderId()).ifPresent(o -> {
            o.setStatus("CONFIRMED");
            o.setTotal(e.getTotal());
            repository.save(o);
        });
    }

    @EventHandler
    void on(OrderRejectedEvent e) {
        repository.findById(e.getOrderId()).ifPresent(o -> {
            o.setStatus("REJECTED");
            o.setReason(e.getReason());
            repository.save(o);
        });
    }
}
