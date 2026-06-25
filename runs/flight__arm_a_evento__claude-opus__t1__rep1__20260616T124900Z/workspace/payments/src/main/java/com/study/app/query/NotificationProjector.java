package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.BookingConfirmedEvent;
import com.study.app.domain.event.BookingRejectedEvent;
import org.springframework.transaction.annotation.Transactional;

/** Reacts to booking decision events (which flow in from the bookings bundle).
 *  bookingId is the PK, so exactly one notification per decision. */
@Projector(version = 1)
public class NotificationProjector {

    private final NotificationRepository notifications;

    public NotificationProjector(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @EventHandler
    @Transactional
    void on(BookingConfirmedEvent e) {
        notifications.save(new NotificationEntity(e.getBookingId(), e.getCustomerId(), "CONFIRMED", null));
    }

    @EventHandler
    @Transactional
    void on(BookingRejectedEvent e) {
        notifications.save(new NotificationEntity(e.getBookingId(), e.getCustomerId(), "REJECTED", e.getReason()));
    }
}
