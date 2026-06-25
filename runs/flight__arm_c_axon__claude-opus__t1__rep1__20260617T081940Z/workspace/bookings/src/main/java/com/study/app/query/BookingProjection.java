package com.study.app.query;

import com.study.app.api.event.BookingEvents.BookingConfirmed;
import com.study.app.api.event.BookingEvents.BookingCreated;
import com.study.app.api.event.BookingEvents.BookingRejected;
import com.study.app.api.query.Queries.FindBooking;
import com.study.app.api.query.Queries.FindNotifications;
import com.study.app.api.query.Queries.FindStats;
import com.study.app.api.query.Views.BookingView;
import com.study.app.api.query.Views.NotificationList;
import com.study.app.api.query.Views.NotificationView;
import com.study.app.api.query.Views.StatsView;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("booking-projection")
public class BookingProjection {

    private final BookingRowRepository bookings;
    private final NotificationRowRepository notifications;

    public BookingProjection(BookingRowRepository bookings, NotificationRowRepository notifications) {
        this.bookings = bookings;
        this.notifications = notifications;
    }

    @EventHandler
    public void on(BookingCreated e) {
        if (!bookings.existsById(e.bookingId())) {
            bookings.save(new BookingRow(e.bookingId(), e.customerId(), e.flightId(), e.seat(), "PENDING"));
        }
    }

    @EventHandler
    public void on(BookingConfirmed e) {
        bookings.findById(e.bookingId()).ifPresent(b -> {
            b.setStatus("CONFIRMED");
            b.setTotal(e.total());
            bookings.save(b);
        });
        emitNotification(e.bookingId(), e.customerId(), "CONFIRMED", null);
    }

    @EventHandler
    public void on(BookingRejected e) {
        bookings.findById(e.bookingId()).ifPresent(b -> {
            b.setStatus("REJECTED");
            b.setReason(e.reason());
            bookings.save(b);
        });
        emitNotification(e.bookingId(), e.customerId(), "REJECTED", e.reason());
    }

    private void emitNotification(String bookingId, String customerId, String status, String reason) {
        if (notifications.existsById(bookingId)) {
            return;
        }
        try {
            notifications.saveAndFlush(new NotificationRow(bookingId, customerId, status, reason));
        } catch (DataIntegrityViolationException duplicate) {
            // exactly-once: a redelivered decision lost the race; ignore
        }
    }

    @QueryHandler
    public BookingView handle(FindBooking q) {
        return bookings.findById(q.bookingId())
                .map(b -> new BookingView(b.getBookingId(), b.getCustomerId(), b.getFlightId(),
                        b.getSeat(), b.getStatus(), b.getReason(), b.getTotal()))
                .orElse(null);
    }

    @QueryHandler
    public NotificationList handle(FindNotifications q) {
        return new NotificationList(notifications.findByCustomerId(q.customerId()).stream()
                .map(n -> new NotificationView(n.getBookingId(), n.getStatus(), n.getReason()))
                .toList());
    }

    @QueryHandler
    public StatsView handle(FindStats q) {
        return new StatsView(bookings.countConfirmed(), bookings.countRejected(), bookings.revenue());
    }
}
