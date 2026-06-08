package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.CreateProductCommand;
import com.study.app.domain.command.ReleaseStockCommand;
import com.study.app.domain.command.ReserveStockCommand;
import com.study.app.domain.command.RestockProductCommand;
import com.study.app.domain.event.ProductCreatedEvent;
import com.study.app.domain.event.ProductRestockedEvent;
import com.study.app.domain.event.StockReleasedEvent;
import com.study.app.domain.event.StockReservedEvent;

@Aggregate(snapshotFrequency = 50)
public class ProductAggregate {

    @AggregateCommandHandler(init = true)
    ProductCreatedEvent handle(CreateProductCommand cmd, ProductAggregateState state) {
        if (cmd.getName() == null || cmd.getName().isBlank())
            throw new IllegalArgumentException("name required");
        if (cmd.getUnitPrice() < 1)
            throw new IllegalArgumentException("unitPrice must be >= 1");
        if (cmd.getStock() < 0)
            throw new IllegalArgumentException("stock must be >= 0");
        return new ProductCreatedEvent(cmd.getProductId(), cmd.getName(), cmd.getUnitPrice(), cmd.getStock());
    }

    @AggregateCommandHandler
    ProductRestockedEvent handle(RestockProductCommand cmd, ProductAggregateState state) {
        if (cmd.getUnits() < 1)
            throw new IllegalArgumentException("units must be >= 1");
        return new ProductRestockedEvent(cmd.getProductId(), cmd.getUnits());
    }

    @AggregateCommandHandler
    StockReservedEvent handle(ReserveStockCommand cmd, ProductAggregateState state) {
        if (state.getStock() < cmd.getQuantity())
            throw new IllegalStateException("OUT_OF_STOCK");
        return new StockReservedEvent(cmd.getProductId(), cmd.getOrderId(), cmd.getQuantity(), state.getUnitPrice());
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
    ProductAggregateState on(ProductRestockedEvent e, ProductAggregateState state) {
        state.setStock(state.getStock() + e.getUnits());
        return state;
    }

    @EventSourcingHandler
    ProductAggregateState on(StockReservedEvent e, ProductAggregateState state) {
        state.setStock(state.getStock() - e.getQuantity());
        return state;
    }

    @EventSourcingHandler
    ProductAggregateState on(StockReleasedEvent e, ProductAggregateState state) {
        state.setStock(state.getStock() + e.getQuantity());
        return state;
    }
}
