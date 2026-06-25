package com.study.app.query;

import com.study.app.domain.SeatStatus;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.FlightCreatedEvent;
import com.study.app.domain.event.SeatBookedEvent;
import com.study.app.domain.event.SeatHeldEvent;
import com.study.app.domain.event.SeatReleasedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Builds the durable flight read model in this service's PostgreSQL. */
@Projector(version = 1)
public class FlightProjector {

    private final FlightRepository flights;
    private final SeatRepository seats;

    public FlightProjector(FlightRepository flights, SeatRepository seats) {
        this.flights = flights;
        this.seats = seats;
    }

    @EventHandler
    @Transactional
    void on(FlightCreatedEvent e) {
        flights.save(new FlightEntity(e.getFlightId(), e.getSeatCount(), e.getSeatPrice()));
        List<SeatEntity> rows = new ArrayList<>();
        for (String seat : e.getSeatIds()) {
            rows.add(new SeatEntity(e.getFlightId(), seat, SeatStatus.AVAILABLE));
        }
        seats.saveAll(rows);
    }

    @EventHandler
    @Transactional
    void on(SeatHeldEvent e) {
        seats.findById(SeatEntity.key(e.getFlightId(), e.getSeat())).ifPresent(s -> {
            s.setStatus(SeatStatus.HELD);
            s.setBookingId(null);
            seats.save(s);
        });
    }

    @EventHandler
    @Transactional
    void on(SeatReleasedEvent e) {
        seats.findById(SeatEntity.key(e.getFlightId(), e.getSeat())).ifPresent(s -> {
            s.setStatus(SeatStatus.AVAILABLE);
            s.setBookingId(null);
            seats.save(s);
        });
    }

    @EventHandler
    @Transactional
    void on(SeatBookedEvent e) {
        seats.findById(SeatEntity.key(e.getFlightId(), e.getSeat())).ifPresent(s -> {
            s.setStatus(SeatStatus.BOOKED);
            s.setBookingId(e.getBookingId());
            seats.save(s);
        });
    }
}
