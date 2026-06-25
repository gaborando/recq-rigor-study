package com.study.app.command;

import com.study.app.domain.*;
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
    private String productId;
    private int quantity;
    private Status status;

    protected OrderAggregate() {}

    @CommandHandler
    public OrderAggregate(com.study.app.command.CreateOrderCommand cmd) {
        apply(new OrderCreatedEvent(cmd.orderId(), cmd.customerId(), cmd.productId(), cmd.quantity()));
    }

    @CommandHandler
    public void handle(com.study.app.command.ConfirmOrderCommand cmd) {
        if (status != Status.PENDING) return; // idempotent / no regression
        apply(new OrderConfirmedEvent(orderId, customerId, cmd.total()));
    }

    @CommandHandler
    public void handle(com.study.app.command.RejectOrderCommand cmd) {
        if (status != Status.PENDING) return; // idempotent / no regression
        apply(new OrderRejectedEvent(orderId, customerId, cmd.reason()));
    }

    @EventSourcingHandler
    public void on(OrderCreatedEvent e) {
        this.orderId = e.orderId();
        this.customerId = e.customerId();
        this.productId = e.productId();
        this.quantity = e.quantity();
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
