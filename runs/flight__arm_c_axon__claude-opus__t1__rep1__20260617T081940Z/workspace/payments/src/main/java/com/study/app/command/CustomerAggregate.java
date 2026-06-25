package com.study.app.command;

import com.study.app.api.command.CustomerCommands.Charge;
import com.study.app.api.command.CustomerCommands.CreateCustomer;
import com.study.app.api.command.CustomerCommands.Deposit;
import com.study.app.api.event.CustomerEvents.Charged;
import com.study.app.api.event.CustomerEvents.ChargeRejected;
import com.study.app.api.event.CustomerEvents.CustomerCreated;
import com.study.app.api.event.CustomerEvents.Deposited;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.HashSet;
import java.util.Set;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

/**
 * One aggregate per customer. Deposits and charges serialize on this stream:
 * the balance is never negative and never double-spent, and concurrent deposits
 * are all applied (no lost update).
 */
@Aggregate
public class CustomerAggregate {

    @AggregateIdentifier
    private String customerId;
    private long balance;
    private final Set<String> chargedBookings = new HashSet<>();

    protected CustomerAggregate() {
    }

    @CommandHandler
    public CustomerAggregate(CreateCustomer cmd) {
        if (cmd.name() == null || cmd.name().isBlank()) {
            throw new IllegalArgumentException("name required");
        }
        if (cmd.balance() < 0) {
            throw new IllegalArgumentException("balance must be >= 0");
        }
        apply(new CustomerCreated(cmd.customerId(), cmd.name(), cmd.balance()));
    }

    @CommandHandler
    public void handle(Deposit cmd) {
        if (cmd.amount() < 1) {
            throw new IllegalArgumentException("amount must be >= 1");
        }
        apply(new Deposited(customerId, cmd.amount()));
    }

    @CommandHandler
    public void handle(Charge cmd) {
        if (chargedBookings.contains(cmd.bookingId())) {
            return;   // idempotent: already charged for this booking
        }
        if (balance >= cmd.amount()) {
            apply(new Charged(customerId, cmd.amount(), cmd.bookingId()));
        } else {
            apply(new ChargeRejected(customerId, cmd.amount(), cmd.bookingId()));
        }
    }

    @EventSourcingHandler
    public void on(CustomerCreated e) {
        this.customerId = e.customerId();
        this.balance = e.balance();
    }

    @EventSourcingHandler
    public void on(Deposited e) {
        this.balance += e.amount();
    }

    @EventSourcingHandler
    public void on(Charged e) {
        this.balance -= e.amount();
        this.chargedBookings.add(e.bookingId());
    }
}
