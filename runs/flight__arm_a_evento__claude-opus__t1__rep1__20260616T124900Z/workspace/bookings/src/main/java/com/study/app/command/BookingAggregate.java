package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.ConfirmBookingCommand;
import com.study.app.domain.command.CreateBookingCommand;
import com.study.app.domain.command.RejectBookingCommand;
import com.study.app.domain.event.BookingConfirmedEvent;
import com.study.app.domain.event.BookingCreatedEvent;
import com.study.app.domain.event.BookingRejectedEvent;

/**
 * The booking is keyed by the client-supplied bookingId. Re-creating the same id
 * is rejected by the framework (AggregateInitializedError) => idempotent. The
 * status guard on the decision handlers makes a booking reach a terminal state
 * exactly once and never regress.
 */
@Aggregate
public class BookingAggregate {

    @AggregateCommandHandler(init = true)
    BookingCreatedEvent handle(CreateBookingCommand cmd) {
        if (cmd.getBookingId() == null || cmd.getBookingId().isBlank())
            throw new IllegalArgumentException("bookingId required");
        if (cmd.getCustomerId() == null || cmd.getCustomerId().isBlank())
            throw new IllegalArgumentException("customerId required");
        if (cmd.getFlightId() == null || cmd.getFlightId().isBlank())
            throw new IllegalArgumentException("flightId required");
        if (cmd.getSeat() == null || cmd.getSeat().isBlank())
            throw new IllegalArgumentException("seat required");
        return new BookingCreatedEvent(cmd.getBookingId(), cmd.getCustomerId(),
                cmd.getFlightId(), cmd.getSeat());
    }

    @AggregateCommandHandler
    BookingConfirmedEvent handle(ConfirmBookingCommand cmd, BookingAggregateState state) {
        if (!BookingAggregateState.PENDING.equals(state.getStatus()))
            throw new IllegalStateException("booking not pending");
        return new BookingConfirmedEvent(cmd.getBookingId(), state.getCustomerId(), cmd.getTotal());
    }

    @AggregateCommandHandler
    BookingRejectedEvent handle(RejectBookingCommand cmd, BookingAggregateState state) {
        if (!BookingAggregateState.PENDING.equals(state.getStatus()))
            throw new IllegalStateException("booking not pending");
        return new BookingRejectedEvent(cmd.getBookingId(), state.getCustomerId(), cmd.getReason());
    }

    @EventSourcingHandler
    BookingAggregateState on(BookingCreatedEvent e, BookingAggregateState state) {
        if (state == null) state = new BookingAggregateState();
        state.setCustomerId(e.getCustomerId());
        state.setFlightId(e.getFlightId());
        state.setSeat(e.getSeat());
        state.setStatus(BookingAggregateState.PENDING);
        return state;
    }

    @EventSourcingHandler
    void on(BookingConfirmedEvent e, BookingAggregateState state) {
        state.setStatus(BookingAggregateState.CONFIRMED);
    }

    @EventSourcingHandler
    void on(BookingRejectedEvent e, BookingAggregateState state) {
        state.setStatus(BookingAggregateState.REJECTED);
    }
}
