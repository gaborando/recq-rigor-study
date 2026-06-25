package com.study.app.query;

import com.study.app.api.event.FlightEvents.FlightCreated;
import com.study.app.api.event.FlightEvents.SeatBooked;
import com.study.app.api.event.FlightEvents.SeatHeld;
import com.study.app.api.event.FlightEvents.SeatReleased;
import com.study.app.api.query.Queries.FindFlight;
import com.study.app.api.query.Views.FlightView;
import com.study.app.api.query.Views.SeatView;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ProcessingGroup("flight-projection")
public class FlightProjection {

    private final FlightRowRepository flights;
    private final SeatRowRepository seats;

    public FlightProjection(FlightRowRepository flights, SeatRowRepository seats) {
        this.flights = flights;
        this.seats = seats;
    }

    @EventHandler
    public void on(FlightCreated e) {
        if (!flights.existsById(e.flightId())) {
            flights.save(new FlightRow(e.flightId(), e.seatCount(), e.seatPrice()));
            for (String seat : e.seats()) {
                seats.save(new SeatRow(e.flightId(), seat));
            }
        }
    }

    @EventHandler
    public void on(SeatHeld e) {
        seats.findById(e.flightId() + ":" + e.seat()).ifPresent(s -> {
            s.setAvailable(false);
            s.setBookingId(null);   // held, not yet owned
            seats.save(s);
        });
    }

    @EventHandler
    public void on(SeatBooked e) {
        seats.findById(e.flightId() + ":" + e.seat()).ifPresent(s -> {
            s.setAvailable(false);
            s.setBookingId(e.bookingId());
            seats.save(s);
        });
    }

    @EventHandler
    public void on(SeatReleased e) {
        seats.findById(e.flightId() + ":" + e.seat()).ifPresent(s -> {
            s.setAvailable(true);
            s.setBookingId(null);
            seats.save(s);
        });
    }

    @QueryHandler
    public FlightView handle(FindFlight q) {
        return flights.findById(q.flightId()).map(f -> {
            List<SeatView> seatViews = seats.findByFlightId(q.flightId()).stream()
                    .map(s -> new SeatView(s.getSeat(), s.isAvailable(), s.getBookingId()))
                    .sorted((a, b) -> a.seat().compareTo(b.seat()))
                    .toList();
            return new FlightView(f.getId(), f.getSeatCount(), f.getSeatPrice(), seatViews);
        }).orElse(null);
    }
}
