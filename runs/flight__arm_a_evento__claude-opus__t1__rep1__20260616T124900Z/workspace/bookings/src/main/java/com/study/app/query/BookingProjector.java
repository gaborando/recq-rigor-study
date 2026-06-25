package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.BookingConfirmedEvent;
import com.study.app.domain.event.BookingCreatedEvent;
import com.study.app.domain.event.BookingRejectedEvent;
import org.springframework.transaction.annotation.Transactional;

@Projector(version = 1)
public class BookingProjector {

    private final BookingRepository bookings;

    public BookingProjector(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @EventHandler
    @Transactional
    void on(BookingCreatedEvent e) {
        BookingEntity b = bookings.findById(e.getBookingId()).orElseGet(BookingEntity::new);
        b.setBookingId(e.getBookingId());
        b.setCustomerId(e.getCustomerId());
        b.setFlightId(e.getFlightId());
        b.setSeat(e.getSeat());
        if (b.getStatus() == null) b.setStatus("PENDING");
        bookings.save(b);
    }

    @EventHandler
    @Transactional
    void on(BookingConfirmedEvent e) {
        bookings.findById(e.getBookingId()).ifPresent(b -> {
            b.setStatus("CONFIRMED");
            b.setTotal(e.getTotal());
            b.setReason(null);
            bookings.save(b);
        });
    }

    @EventHandler
    @Transactional
    void on(BookingRejectedEvent e) {
        bookings.findById(e.getBookingId()).ifPresent(b -> {
            b.setStatus("REJECTED");
            b.setReason(e.getReason());
            b.setTotal(null);
            bookings.save(b);
        });
    }
}
