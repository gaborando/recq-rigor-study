package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

@Projector(version = 1)
public class NotificationProjector {

    private final NotificationRepository repository;

    public NotificationProjector(NotificationRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        if (!repository.existsById(e.getOrderId())) {
            repository.save(new NotificationEntity(e.getOrderId(), e.getCustomerId(), "CONFIRMED", null));
        }
    }

    @EventHandler
    void on(OrderRejectedEvent e) {
        if (!repository.existsById(e.getOrderId())) {
            repository.save(new NotificationEntity(e.getOrderId(), e.getCustomerId(), "REJECTED", e.getReason()));
        }
    }
}
