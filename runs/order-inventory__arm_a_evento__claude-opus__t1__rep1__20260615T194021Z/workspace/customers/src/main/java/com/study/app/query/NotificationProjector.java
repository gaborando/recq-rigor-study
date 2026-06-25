package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

/**
 * Emits exactly one notification per order decision. Keyed by orderId (idempotent
 * upsert) so duplicate/redelivered decision events never produce a second row.
 */
@Projector(version = 1)
public class NotificationProjector {

    private final NotificationRepository repository;

    public NotificationProjector(NotificationRepository repository) {
        this.repository = repository;
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderConfirmedEvent e) {
        repository.save(new NotificationEntity(e.getOrderId(), e.getCustomerId(), "CONFIRMED", null));
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderRejectedEvent e) {
        repository.save(new NotificationEntity(e.getOrderId(), e.getCustomerId(), "REJECTED", e.getReason()));
    }
}
