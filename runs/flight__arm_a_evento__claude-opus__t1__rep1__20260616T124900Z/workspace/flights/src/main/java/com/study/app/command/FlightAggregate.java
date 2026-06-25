package com.study.app.command;

import com.study.app.domain.SeatStatus;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.SeatIds;
import com.study.app.domain.command.ConfirmSeatCommand;
import com.study.app.domain.command.CreateFlightCommand;
import com.study.app.domain.command.HoldSeatCommand;
import com.study.app.domain.command.ReleaseSeatCommand;
import com.study.app.domain.event.FlightCreatedEvent;
import com.study.app.domain.event.SeatBookedEvent;
import com.study.app.domain.event.SeatHeldEvent;
import com.study.app.domain.event.SeatReleasedEvent;

import java.util.List;

/**
 * The flight is the consistency boundary for its seats. Because Evento serializes
 * commands per aggregate id (the flight), concurrent HOLD requests for the same
 * seat are ordered: the first wins, the rest see the seat already taken and are
 * rejected. A seat is therefore never double-booked.
 */
@Aggregate
public class FlightAggregate {

    @AggregateCommandHandler(init = true)
    FlightCreatedEvent handle(CreateFlightCommand cmd) {
        if (cmd.getSeatCount() < 1) throw new IllegalArgumentException("seatCount < 1");
        if (cmd.getSeatPrice() < 1) throw new IllegalArgumentException("seatPrice < 1");
        List<String> seats = SeatIds.generate(cmd.getSeatCount());
        return new FlightCreatedEvent(cmd.getFlightId(), cmd.getSeatCount(), cmd.getSeatPrice(), seats);
    }

    @AggregateCommandHandler
    SeatHeldEvent handle(HoldSeatCommand cmd, FlightAggregateState state) {
        String seat = cmd.getSeat();
        if (!state.hasSeat(seat)) throw new IllegalArgumentException("unknown seat: " + seat);
        String st = state.statusOf(seat);
        if (SeatStatus.AVAILABLE.equals(st)) {
            return new SeatHeldEvent(cmd.getFlightId(), seat, cmd.getBookingId(), state.getSeatPrice());
        }
        // idempotent: already held by this same booking
        if (SeatStatus.HELD.equals(st) && cmd.getBookingId().equals(state.holderOf(seat))) {
            return new SeatHeldEvent(cmd.getFlightId(), seat, cmd.getBookingId(), state.getSeatPrice());
        }
        throw new IllegalStateException("SEAT_TAKEN");
    }

    @AggregateCommandHandler
    SeatReleasedEvent handle(ReleaseSeatCommand cmd, FlightAggregateState state) {
        String seat = cmd.getSeat();
        if (SeatStatus.HELD.equals(state.statusOf(seat))
                && cmd.getBookingId().equals(state.holderOf(seat))) {
            return new SeatReleasedEvent(cmd.getFlightId(), seat, cmd.getBookingId());
        }
        throw new IllegalStateException("seat not held by booking " + cmd.getBookingId());
    }

    @AggregateCommandHandler
    SeatBookedEvent handle(ConfirmSeatCommand cmd, FlightAggregateState state) {
        String seat = cmd.getSeat();
        if (SeatStatus.BOOKED.equals(state.statusOf(seat))
                && cmd.getBookingId().equals(state.holderOf(seat))) {
            return new SeatBookedEvent(cmd.getFlightId(), seat, cmd.getBookingId()); // idempotent
        }
        if (SeatStatus.HELD.equals(state.statusOf(seat))
                && cmd.getBookingId().equals(state.holderOf(seat))) {
            return new SeatBookedEvent(cmd.getFlightId(), seat, cmd.getBookingId());
        }
        throw new IllegalStateException("seat not held by booking " + cmd.getBookingId());
    }

    @EventSourcingHandler
    FlightAggregateState on(FlightCreatedEvent e, FlightAggregateState state) {
        if (state == null) state = new FlightAggregateState();
        state.setSeatPrice(e.getSeatPrice());
        for (String seat : e.getSeatIds()) {
            state.getStatus().put(seat, SeatStatus.AVAILABLE);
        }
        return state;
    }

    @EventSourcingHandler
    void on(SeatHeldEvent e, FlightAggregateState state) {
        state.getStatus().put(e.getSeat(), SeatStatus.HELD);
        state.getHolder().put(e.getSeat(), e.getBookingId());
    }

    @EventSourcingHandler
    void on(SeatReleasedEvent e, FlightAggregateState state) {
        state.getStatus().put(e.getSeat(), SeatStatus.AVAILABLE);
        state.getHolder().remove(e.getSeat());
    }

    @EventSourcingHandler
    void on(SeatBookedEvent e, FlightAggregateState state) {
        state.getStatus().put(e.getSeat(), SeatStatus.BOOKED);
        state.getHolder().put(e.getSeat(), e.getBookingId());
    }
}
