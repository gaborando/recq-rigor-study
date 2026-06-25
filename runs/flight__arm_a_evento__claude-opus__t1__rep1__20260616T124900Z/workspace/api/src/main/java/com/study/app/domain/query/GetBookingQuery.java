package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.BookingView;

public class GetBookingQuery extends Query<Single<BookingView>> {
    private String bookingId;

    public GetBookingQuery() {}
    public GetBookingQuery(String bookingId) { this.bookingId = bookingId; }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
}
