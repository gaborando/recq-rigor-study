package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetBookingQuery;
import com.study.app.domain.view.BookingView;

import java.util.NoSuchElementException;

@Projection
public class BookingProjection {

    private final BookingRepository bookings;

    public BookingProjection(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @QueryHandler
    Single<BookingView> query(GetBookingQuery q) {
        BookingEntity b = bookings.findById(q.getBookingId())
                .orElseThrow(() -> new NoSuchElementException("booking not found: " + q.getBookingId()));
        BookingView v = new BookingView();
        v.setBookingId(b.getBookingId());
        v.setCustomerId(b.getCustomerId());
        v.setFlightId(b.getFlightId());
        v.setSeat(b.getSeat());
        v.setStatus(b.getStatus());
        v.setReason(b.getReason());
        v.setTotal(b.getTotal());
        return Single.of(v);
    }
}
