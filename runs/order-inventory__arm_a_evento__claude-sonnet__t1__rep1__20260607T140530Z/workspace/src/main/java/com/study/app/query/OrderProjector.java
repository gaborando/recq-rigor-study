package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderPlacedEvent;
import com.study.app.domain.event.OrderRejectedEvent;
import com.study.app.query.entity.OrderEntity;
import com.study.app.query.repository.OrderRepository;

@Projector(version = 1)
public class OrderProjector {

    private final OrderRepository orderRepository;

    public OrderProjector(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @EventHandler
    void on(OrderPlacedEvent e) {
        var entity = new OrderEntity();
        entity.setOrderId(e.getOrderId());
        entity.setCustomerId(e.getCustomerId());
        entity.setProductId(e.getProductId());
        entity.setQuantity(e.getQuantity());
        entity.setStatus("PENDING");
        orderRepository.save(entity);
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        orderRepository.findById(e.getOrderId()).ifPresent(o -> {
            o.setStatus("CONFIRMED");
            o.setTotal(e.getTotal());
            orderRepository.save(o);
        });
    }

    @EventHandler
    void on(OrderRejectedEvent e) {
        orderRepository.findById(e.getOrderId()).ifPresent(o -> {
            o.setStatus("REJECTED");
            o.setReason(e.getReason());
            orderRepository.save(o);
        });
    }
}
