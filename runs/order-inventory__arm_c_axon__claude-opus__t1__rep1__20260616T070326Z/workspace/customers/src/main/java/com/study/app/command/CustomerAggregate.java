package com.study.app.command;

import com.study.app.domain.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import java.util.HashSet;
import java.util.Set;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class CustomerAggregate {

    @AggregateIdentifier
    private String customerId;
    private int balance;
    // orderIds for which a charge decision (charged or failed) was already made
    private final Set<String> chargeDecisions = new HashSet<>();

    protected CustomerAggregate() {}

    @CommandHandler
    public CustomerAggregate(com.study.app.command.CreateCustomerCommand cmd) {
        apply(new CustomerCreatedEvent(cmd.customerId(), cmd.name(), cmd.balance()));
    }

    @CommandHandler
    public void handle(com.study.app.command.DepositCommand cmd) {
        apply(new FundsDepositedEvent(customerId, cmd.amount()));
    }

    @CommandHandler
    public void handle(com.study.app.command.ChargeCommand cmd) {
        if (chargeDecisions.contains(cmd.orderId())) {
            return; // idempotent: decision already taken for this order
        }
        if (balance >= cmd.total()) {
            apply(new ChargedEvent(customerId, cmd.orderId(), cmd.total()));
        } else {
            apply(new ChargeFailedEvent(customerId, cmd.orderId()));
        }
    }

    @EventSourcingHandler
    public void on(CustomerCreatedEvent e) {
        this.customerId = e.customerId();
        this.balance = e.balance();
    }

    @EventSourcingHandler
    public void on(FundsDepositedEvent e) {
        this.balance += e.amount();
    }

    @EventSourcingHandler
    public void on(ChargedEvent e) {
        this.balance -= e.total();
        this.chargeDecisions.add(e.orderId());
    }

    @EventSourcingHandler
    public void on(ChargeFailedEvent e) {
        this.chargeDecisions.add(e.orderId());
    }
}
