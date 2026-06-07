package com.study.app.command;

import com.study.app.domain.events.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class OrderAggregate {

    enum Status { PENDING, CONFIRMED, REJECTED }

    @AggregateIdentifier
    private String orderId;
    private String customerId;
    private Status status;

    protected OrderAggregate() {}

    @CommandHandler
    public OrderAggregate(CreateOrderCommand cmd) {
        if (cmd.quantity() < 1) throw new IllegalArgumentException("quantity must be >= 1");
        apply(new OrderCreatedEvent(cmd.orderId(), cmd.customerId(), cmd.productId(), cmd.quantity()));
    }

    @CommandHandler
    public void handle(ConfirmOrderCommand cmd) {
        if (status != Status.PENDING) return;
        apply(new OrderConfirmedEvent(orderId, cmd.total()));
    }

    @CommandHandler
    public void handle(RejectOrderCommand cmd) {
        if (status != Status.PENDING) return;
        apply(new OrderRejectedEvent(orderId, cmd.reason()));
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent e) {
        this.orderId = e.orderId();
        this.customerId = e.customerId();
        this.status = Status.PENDING;
    }

    @EventSourcingHandler
    public void on(OrderConfirmedEvent e) {
        this.status = Status.CONFIRMED;
    }

    @EventSourcingHandler
    public void on(OrderRejectedEvent e) {
        this.status = Status.REJECTED;
    }
}
