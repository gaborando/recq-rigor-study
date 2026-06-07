package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.command.PlaceOrderCommand;
import com.study.app.domain.command.RejectOrderCommand;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderPlacedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

@Aggregate(snapshotFrequency = 10)
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderPlacedEvent handle(PlaceOrderCommand cmd, OrderAggregateState state) {
        return new OrderPlacedEvent(cmd.getOrderId(), cmd.getCustomerId(), cmd.getProductId(), cmd.getQuantity());
    }

    @AggregateCommandHandler
    OrderConfirmedEvent handle(ConfirmOrderCommand cmd, OrderAggregateState state) {
        return new OrderConfirmedEvent(cmd.getOrderId(), state.getCustomerId(), cmd.getTotal());
    }

    @AggregateCommandHandler
    OrderRejectedEvent handle(RejectOrderCommand cmd, OrderAggregateState state) {
        return new OrderRejectedEvent(cmd.getOrderId(), state.getCustomerId(), cmd.getReason());
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderPlacedEvent e, OrderAggregateState state) {
        if (state == null) state = new OrderAggregateState();
        state.setCustomerId(e.getCustomerId());
        state.setProductId(e.getProductId());
        state.setQuantity(e.getQuantity());
        state.setStatus("PENDING");
        return state;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderConfirmedEvent e, OrderAggregateState state) {
        state.setStatus("CONFIRMED");
        return state;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderRejectedEvent e, OrderAggregateState state) {
        state.setStatus("REJECTED");
        return state;
    }
}
