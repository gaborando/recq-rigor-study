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
public class ProductAggregate {

    @AggregateIdentifier
    private String productId;
    private int unitPrice;
    private int stock;
    // orderIds for which a reservation decision (reserved or failed) was already made
    private final Set<String> reservationDecisions = new HashSet<>();
    // orderIds whose reservation has already been released (idempotent compensation)
    private final Set<String> released = new HashSet<>();

    protected ProductAggregate() {}

    @CommandHandler
    public ProductAggregate(com.study.app.command.CreateProductCommand cmd) {
        apply(new ProductCreatedEvent(cmd.productId(), cmd.name(), cmd.unitPrice(), cmd.stock()));
    }

    @CommandHandler
    public void handle(com.study.app.command.RestockCommand cmd) {
        apply(new StockRestockedEvent(productId, cmd.units()));
    }

    @CommandHandler
    public void handle(com.study.app.command.ReserveStockCommand cmd) {
        if (reservationDecisions.contains(cmd.orderId())) {
            return; // idempotent: decision already taken for this order
        }
        if (stock >= cmd.quantity()) {
            apply(new StockReservedEvent(productId, cmd.orderId(), cmd.quantity(),
                    unitPrice, cmd.quantity() * unitPrice));
        } else {
            apply(new StockReservationFailedEvent(productId, cmd.orderId()));
        }
    }

    @CommandHandler
    public void handle(com.study.app.command.ReleaseStockCommand cmd) {
        if (released.contains(cmd.orderId())) {
            return; // idempotent
        }
        apply(new StockReleasedEvent(productId, cmd.orderId(), cmd.quantity()));
    }

    @EventSourcingHandler
    public void on(ProductCreatedEvent e) {
        this.productId = e.productId();
        this.unitPrice = e.unitPrice();
        this.stock = e.stock();
    }

    @EventSourcingHandler
    public void on(StockRestockedEvent e) {
        this.stock += e.units();
    }

    @EventSourcingHandler
    public void on(StockReservedEvent e) {
        this.stock -= e.quantity();
        this.reservationDecisions.add(e.orderId());
    }

    @EventSourcingHandler
    public void on(StockReservationFailedEvent e) {
        this.reservationDecisions.add(e.orderId());
    }

    @EventSourcingHandler
    public void on(StockReleasedEvent e) {
        this.stock += e.quantity();
        this.released.add(e.orderId());
    }
}
