package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.study.app.domain.command.CreateProductCommand;
import com.study.app.domain.command.ReleaseStockCommand;
import com.study.app.domain.command.ReserveStockCommand;
import com.study.app.domain.command.RestockProductCommand;
import com.study.app.domain.event.ProductCreatedEvent;
import com.study.app.domain.event.StockReleasedEvent;
import com.study.app.domain.event.StockReservationFailedEvent;
import com.study.app.domain.event.StockReservedEvent;
import com.study.app.domain.event.StockRestockedEvent;

@Aggregate(snapshotFrequency = 10)
public class ProductAggregate {

    @AggregateCommandHandler(init = true)
    ProductCreatedEvent handle(CreateProductCommand cmd, ProductAggregateState state) {
        return new ProductCreatedEvent(cmd.getProductId(), cmd.getName(), cmd.getUnitPrice(), cmd.getStock());
    }

    @AggregateCommandHandler
    StockRestockedEvent handle(RestockProductCommand cmd, ProductAggregateState state) {
        return new StockRestockedEvent(cmd.getProductId(), cmd.getUnits());
    }

    @AggregateCommandHandler
    DomainEvent handle(ReserveStockCommand cmd, ProductAggregateState state) {
        if (state.getStock() >= cmd.getQuantity()) {
            long total = (long) cmd.getQuantity() * state.getUnitPrice();
            return new StockReservedEvent(cmd.getProductId(), cmd.getOrderId(), cmd.getQuantity(),
                    state.getUnitPrice(), total);
        } else {
            return new StockReservationFailedEvent(cmd.getProductId(), cmd.getOrderId(), cmd.getQuantity());
        }
    }

    @AggregateCommandHandler
    StockReleasedEvent handle(ReleaseStockCommand cmd, ProductAggregateState state) {
        return new StockReleasedEvent(cmd.getProductId(), cmd.getOrderId(), cmd.getQuantity());
    }

    @EventSourcingHandler
    ProductAggregateState on(ProductCreatedEvent e, ProductAggregateState state) {
        if (state == null) state = new ProductAggregateState();
        state.setName(e.getName());
        state.setUnitPrice(e.getUnitPrice());
        state.setStock(e.getStock());
        return state;
    }

    @EventSourcingHandler
    ProductAggregateState on(StockRestockedEvent e, ProductAggregateState state) {
        state.setStock(state.getStock() + e.getUnits());
        return state;
    }

    @EventSourcingHandler
    ProductAggregateState on(StockReservedEvent e, ProductAggregateState state) {
        state.setStock(state.getStock() - e.getQuantity());
        return state;
    }

    @EventSourcingHandler
    ProductAggregateState on(StockReservationFailedEvent e, ProductAggregateState state) {
        return state;
    }

    @EventSourcingHandler
    ProductAggregateState on(StockReleasedEvent e, ProductAggregateState state) {
        state.setStock(state.getStock() + e.getQuantity());
        return state;
    }
}
