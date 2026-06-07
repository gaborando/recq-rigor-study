package com.study.app.command;

import com.study.app.domain.events.*;
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
    private String name;
    private int balance;
    private Set<String> processedChargeOrders = new HashSet<>();

    protected CustomerAggregate() {}

    @CommandHandler
    public CustomerAggregate(CreateCustomerCommand cmd) {
        apply(new CustomerCreatedEvent(cmd.customerId(), cmd.name(), cmd.balance()));
    }

    @CommandHandler
    public void handle(DepositFundsCommand cmd) {
        apply(new FundsDepositedEvent(customerId, cmd.amount()));
    }

    @CommandHandler
    public void handle(ChargeFundsCommand cmd) {
        if (processedChargeOrders.contains(cmd.orderId())) {
            return; // idempotent: already processed
        }
        if (balance >= cmd.amount()) {
            apply(new FundsChargedEvent(customerId, cmd.orderId(), cmd.amount()));
        } else {
            apply(new ChargeFailedEvent(customerId, cmd.orderId(), cmd.productId(), cmd.quantity()));
        }
    }

    @EventSourcingHandler
    public void on(CustomerCreatedEvent e) {
        this.customerId = e.customerId();
        this.name = e.name();
        this.balance = e.balance();
    }

    @EventSourcingHandler
    public void on(FundsDepositedEvent e) {
        this.balance += e.amount();
    }

    @EventSourcingHandler
    public void on(FundsChargedEvent e) {
        this.balance -= e.amount();
        this.processedChargeOrders.add(e.orderId());
    }

    @EventSourcingHandler
    public void on(ChargeFailedEvent e) {
        this.processedChargeOrders.add(e.orderId());
    }
}
