package com.study.app.command;

import com.study.app.domain.events.*;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;

import static org.axonframework.modelling.command.AggregateLifecycle.apply;

@Aggregate
public class ProductAggregate {

    @AggregateIdentifier
    private String productId;
    private String name;
    private int unitPrice;
    private int availableStock;

    protected ProductAggregate() {}

    @CommandHandler
    public ProductAggregate(CreateProductCommand cmd) {
        apply(new ProductCreatedEvent(cmd.productId(), cmd.name(), cmd.unitPrice(), cmd.stock()));
    }

    @CommandHandler
    public void handle(RestockProductCommand cmd) {
        apply(new ProductRestockedEvent(productId, cmd.units()));
    }

    @CommandHandler
    public void handle(ReserveStockCommand cmd) {
        if (availableStock >= cmd.quantity()) {
            int total = unitPrice * cmd.quantity();
            apply(new StockReservedEvent(productId, cmd.orderId(), cmd.customerId(), cmd.quantity(), total));
        } else {
            apply(new StockReservationFailedEvent(productId, cmd.orderId(), "OUT_OF_STOCK"));
        }
    }

    @CommandHandler
    public void handle(ReleaseStockCommand cmd) {
        apply(new StockReleasedEvent(productId, cmd.orderId(), cmd.quantity()));
    }

    @EventSourcingHandler
    public void on(ProductCreatedEvent e) {
        this.productId = e.productId();
        this.name = e.name();
        this.unitPrice = e.unitPrice();
        this.availableStock = e.stock();
    }

    @EventSourcingHandler
    public void on(ProductRestockedEvent e) {
        this.availableStock += e.units();
    }

    @EventSourcingHandler
    public void on(StockReservedEvent e) {
        this.availableStock -= e.quantity();
    }

    @EventSourcingHandler
    public void on(StockReservationFailedEvent e) {}

    @EventSourcingHandler
    public void on(StockReleasedEvent e) {
        this.availableStock += e.quantity();
    }
}
