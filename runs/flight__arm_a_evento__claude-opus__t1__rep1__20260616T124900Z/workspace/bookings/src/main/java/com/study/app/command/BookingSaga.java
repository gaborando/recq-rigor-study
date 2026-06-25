package com.study.app.command;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.evento.common.modeling.messaging.payload.Command;
import com.study.app.domain.command.ChargeCommand;
import com.study.app.domain.command.ConfirmBookingCommand;
import com.study.app.domain.command.ConfirmSeatCommand;
import com.study.app.domain.command.HoldSeatCommand;
import com.study.app.domain.command.RejectBookingCommand;
import com.study.app.domain.command.ReleaseSeatCommand;
import com.study.app.domain.event.BookingCreatedEvent;
import com.study.app.domain.event.SeatHeldEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates the booking flow across the flight (HOLD), customer (CHARGE) and
 * booking aggregates:
 *
 *   HOLD seat ── fail ─> REJECTED(SEAT_TAKEN)            (nothing charged)
 *      │ ok
 *   CHARGE customer ── fail ─> RELEASE seat; REJECTED(INSUFFICIENT_FUNDS)
 *      │ ok
 *   CONFIRM seat; CONFIRM booking                        (CONFIRMED)
 *
 * The branch is decided by *which* command failed, so it never inspects exception
 * types. After a successful charge no exception is ever rethrown, so the saga
 * handler is never retried — the customer is charged exactly once.
 */
@Saga(version = 1)
public class BookingSaga {

    private static final Logger log = LoggerFactory.getLogger(BookingSaga.class);

    @SagaEventHandler(init = true, associationProperty = "bookingId")
    public BookingSagaState on(BookingCreatedEvent e, CommandGateway cg) {
        BookingSagaState state = new BookingSagaState();
        state.setAssociation("bookingId", e.getBookingId());
        state.setBookingId(e.getBookingId());
        state.setPhase("STARTED");

        // 1. HOLD the seat (the distributed lock).
        SeatHeldEvent held;
        try {
            held = cg.<SeatHeldEvent>send(
                    new HoldSeatCommand(e.getFlightId(), e.getSeat(), e.getBookingId())).get();
        } catch (Exception ex) {
            log.info("hold failed for booking {}: {}", e.getBookingId(), rootMessage(ex));
            reject(cg, e.getBookingId(), "SEAT_TAKEN");
            state.setPhase("REJECTED_SEAT_TAKEN");
            state.setEnded(true);
            return state;
        }

        // 2. CHARGE the customer the seat price.
        int price = held.getSeatPrice();
        try {
            cg.send(new ChargeCommand(e.getCustomerId(), price, e.getBookingId())).get();
        } catch (Exception ex) {
            log.info("charge failed for booking {}: {}", e.getBookingId(), rootMessage(ex));
            // compensation: release the held seat, then reject.
            safe(cg, new ReleaseSeatCommand(e.getFlightId(), e.getSeat(), e.getBookingId()));
            reject(cg, e.getBookingId(), "INSUFFICIENT_FUNDS");
            state.setPhase("REJECTED_INSUFFICIENT_FUNDS");
            state.setEnded(true);
            return state;
        }

        // 3. CONFIRM: own the seat and confirm the booking. Charge already
        //    succeeded, so we never rethrow here (no retry, no double charge).
        safe(cg, new ConfirmSeatCommand(e.getFlightId(), e.getSeat(), e.getBookingId()));
        try {
            cg.send(new ConfirmBookingCommand(e.getBookingId(), price)).get();
        } catch (Exception ex) {
            log.warn("confirm booking failed for {} (already charged): {}",
                    e.getBookingId(), rootMessage(ex));
        }
        state.setPhase("CONFIRMED");
        state.setEnded(true);
        return state;
    }

    private void reject(CommandGateway cg, String bookingId, String reason) {
        safe(cg, new RejectBookingCommand(bookingId, reason));
    }

    private void safe(CommandGateway cg, Command cmd) {
        try {
            cg.send(cmd).get();
        } catch (Exception ex) {
            log.warn("command {} failed: {}", cmd.getClass().getSimpleName(), rootMessage(ex));
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage();
    }
}
