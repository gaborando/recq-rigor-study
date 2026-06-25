package com.study.app.command;

import com.study.app.api.command.FlightCommands.ConfirmSeat;
import com.study.app.api.command.FlightCommands.CreateFlight;
import com.study.app.api.command.FlightCommands.HoldSeat;
import com.study.app.api.command.FlightCommands.ReleaseSeat;
import com.study.app.api.event.FlightEvents.FlightCreated;
import com.study.app.api.event.FlightEvents.SeatBooked;
import com.study.app.api.event.FlightEvents.SeatHeld;
import com.study.app.api.event.FlightEvents.SeatHoldRejected;
import com.study.app.api.event.FlightEvents.SeatReleased;
import com.study.app.api.support.Seats;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.HashMap;
import java.util.Map;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * One aggregate per flight. All commands for a flight serialize on this
 * aggregate's event stream, so the per-seat HOLD is a genuine distributed lock:
 * exactly one concurrent requester wins a given seat — never double-booked.
 */
@Aggregate
public class FlightAggregate {

    private enum SeatStatus { AVAILABLE, HELD, BOOKED }

    @AggregateIdentifier
    private String flightId;
    private int seatPrice;

    private final Map<String, SeatStatus> status = new HashMap<>();
    private final Map<String, String> holder = new HashMap<>();

    protected FlightAggregate() {
    }

    @CommandHandler
    public FlightAggregate(CreateFlight cmd) {
        if (cmd.seatCount() < 1) {
            throw new IllegalArgumentException("seatCount must be >= 1");
        }
        if (cmd.seatPrice() < 1) {
            throw new IllegalArgumentException("seatPrice must be >= 1");
        }
        apply(new FlightCreated(cmd.flightId(), cmd.seatCount(), cmd.seatPrice(),
                Seats.labels(cmd.seatCount())));
    }

    @CommandHandler
    public void handle(HoldSeat cmd) {
        SeatStatus s = status.get(cmd.seat());
        if (s == null) {
            // unknown seat for this flight -> reject (acceptable per spec)
            apply(new SeatHoldRejected(flightId, cmd.seat(), cmd.bookingId()));
            return;
        }
        if (cmd.bookingId().equals(holder.get(cmd.seat()))) {
            // idempotent: this booking already owns/holds the seat
            return;
        }
        if (s == SeatStatus.AVAILABLE) {
            apply(new SeatHeld(flightId, cmd.seat(), cmd.bookingId(), seatPrice));
        } else {
            apply(new SeatHoldRejected(flightId, cmd.seat(), cmd.bookingId()));
        }
    }

    @CommandHandler
    public void handle(ConfirmSeat cmd) {
        if (status.get(cmd.seat()) == SeatStatus.HELD
                && cmd.bookingId().equals(holder.get(cmd.seat()))) {
            apply(new SeatBooked(flightId, cmd.seat(), cmd.bookingId()));
        }
    }

    @CommandHandler
    public void handle(ReleaseSeat cmd) {
        if (status.get(cmd.seat()) == SeatStatus.HELD
                && cmd.bookingId().equals(holder.get(cmd.seat()))) {
            apply(new SeatReleased(flightId, cmd.seat(), cmd.bookingId()));
        }
    }

    @EventSourcingHandler
    public void on(FlightCreated e) {
        this.flightId = e.flightId();
        this.seatPrice = e.seatPrice();
        for (String seat : e.seats()) {
            status.put(seat, SeatStatus.AVAILABLE);
        }
    }

    @EventSourcingHandler
    public void on(SeatHeld e) {
        status.put(e.seat(), SeatStatus.HELD);
        holder.put(e.seat(), e.bookingId());
    }

    @EventSourcingHandler
    public void on(SeatBooked e) {
        status.put(e.seat(), SeatStatus.BOOKED);
        holder.put(e.seat(), e.bookingId());
    }

    @EventSourcingHandler
    public void on(SeatReleased e) {
        status.put(e.seat(), SeatStatus.AVAILABLE);
        holder.remove(e.seat());
    }
}
