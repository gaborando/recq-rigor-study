package com.study.app.command;

import com.study.app.api.command.BookingCommands.ConfirmBooking;
import com.study.app.api.command.BookingCommands.CreateBooking;
import com.study.app.api.command.BookingCommands.RejectBooking;
import com.study.app.api.event.BookingEvents.BookingConfirmed;
import com.study.app.api.event.BookingEvents.BookingCreated;
import com.study.app.api.event.BookingEvents.BookingRejected;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateCreationPolicy;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CreationPolicy;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * One aggregate per client-supplied bookingId. Creation is idempotent: a retry
 * storm of the same bookingId cannot create a second booking (optimistic
 * concurrency on sequence 0 + the already-created guard). The decision is
 * emitted exactly once, so status never regresses and there is exactly one
 * notification per booking.
 */
@Aggregate
public class BookingAggregate {

    private enum Status { PENDING, CONFIRMED, REJECTED }

    @AggregateIdentifier
    private String bookingId;
    private Status status;

    protected BookingAggregate() {
    }

    @CommandHandler
    @CreationPolicy(AggregateCreationPolicy.CREATE_IF_MISSING)
    public void handle(CreateBooking cmd) {
        if (status != null) {
            return;   // idempotent replay: booking already exists
        }
        apply(new BookingCreated(cmd.bookingId(), cmd.customerId(), cmd.flightId(), cmd.seat()));
    }

    @CommandHandler
    public void handle(ConfirmBooking cmd) {
        if (status != Status.PENDING) {
            return;   // already decided; never regress
        }
        // customerId carried for the notification projection
        apply(new BookingConfirmed(bookingId, customerId, cmd.total()));
    }

    @CommandHandler
    public void handle(RejectBooking cmd) {
        if (status != Status.PENDING) {
            return;
        }
        apply(new BookingRejected(bookingId, customerId, cmd.reason()));
    }

    private String customerId;

    @EventSourcingHandler
    public void on(BookingCreated e) {
        this.bookingId = e.bookingId();
        this.customerId = e.customerId();
        this.status = Status.PENDING;
    }

    @EventSourcingHandler
    public void on(BookingConfirmed e) {
        this.status = Status.CONFIRMED;
    }

    @EventSourcingHandler
    public void on(BookingRejected e) {
        this.status = Status.REJECTED;
    }
}
