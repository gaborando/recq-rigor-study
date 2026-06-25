package com.study.app.query;

import com.study.app.domain.SeatStatus;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetFlightQuery;
import com.study.app.domain.view.FlightView;
import com.study.app.domain.view.SeatView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Projection
public class FlightProjection {

    private final FlightRepository flights;
    private final SeatRepository seats;

    public FlightProjection(FlightRepository flights, SeatRepository seats) {
        this.flights = flights;
        this.seats = seats;
    }

    @QueryHandler
    Single<FlightView> query(GetFlightQuery q) {
        FlightEntity flight = flights.findById(q.getFlightId())
                .orElseThrow(() -> new NoSuchElementException("flight not found: " + q.getFlightId()));
        List<SeatEntity> rows = new ArrayList<>(seats.findByFlightId(q.getFlightId()));
        rows.sort(Comparator.comparing(SeatEntity::getSeat));
        List<SeatView> seatViews = new ArrayList<>();
        for (SeatEntity s : rows) {
            boolean available = SeatStatus.AVAILABLE.equals(s.getStatus());
            String bookingId = SeatStatus.BOOKED.equals(s.getStatus()) ? s.getBookingId() : null;
            seatViews.add(new SeatView(s.getSeat(), available, bookingId));
        }
        return Single.of(new FlightView(flight.getId(), flight.getSeatCount(), flight.getSeatPrice(), seatViews));
    }
}
