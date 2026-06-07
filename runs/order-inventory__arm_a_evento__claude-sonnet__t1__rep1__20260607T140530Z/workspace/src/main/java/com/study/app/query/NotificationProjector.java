package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderRejectedEvent;
import com.study.app.query.entity.NotificationEntity;
import com.study.app.query.repository.NotificationRepository;

@Projector(version = 1)
public class NotificationProjector {

    private final NotificationRepository notificationRepository;

    public NotificationProjector(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventHandler
    void on(OrderConfirmedEvent e) {
        if (!notificationRepository.existsByOrderId(e.getOrderId())) {
            notificationRepository.save(
                    new NotificationEntity(e.getOrderId(), e.getCustomerId(), "CONFIRMED", null));
        }
    }

    @EventHandler
    void on(OrderRejectedEvent e) {
        if (!notificationRepository.existsByOrderId(e.getOrderId())) {
            notificationRepository.save(
                    new NotificationEntity(e.getOrderId(), e.getCustomerId(), "REJECTED", e.getReason()));
        }
    }
}
