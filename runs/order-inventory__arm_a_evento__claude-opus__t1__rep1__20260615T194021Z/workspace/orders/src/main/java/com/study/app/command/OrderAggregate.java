package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.command.CreateOrderCommand;
import com.study.app.domain.command.RejectOrderCommand;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderCreatedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

/**
 * Order aggregate — the consistency boundary for an order's lifecycle. The
 * client-supplied orderId is the aggregate id, so a duplicate create is rejected
 * by the platform (AggregateInitializedError). A decision (confirm/reject) is
 * accepted only while PENDING, guaranteeing exactly one decision event and that
 * status never regresses.
 */
@Aggregate
public class OrderAggregate {

    @AggregateCommandHandler(init = true)
    OrderCreatedEvent handle(CreateOrderCommand cmd, OrderAggregateState state) {
        if (cmd.getQuantity() < 1)
            throw new IllegalArgumentException("quantity must be >= 1");
        return new OrderCreatedEvent(cmd.getOrderId(), cmd.getCustomerId(), cmd.getProductId(), cmd.getQuantity());
    }

    @AggregateCommandHandler
    OrderConfirmedEvent handle(ConfirmOrderCommand cmd, OrderAggregateState state) {
        if (!"PENDING".equals(state.getStatus()))
            throw new IllegalStateException("order already decided: " + state.getStatus());
        return new OrderConfirmedEvent(cmd.getOrderId(), state.getCustomerId(), state.getProductId(),
                state.getQuantity(), cmd.getTotal());
    }

    @AggregateCommandHandler
    OrderRejectedEvent handle(RejectOrderCommand cmd, OrderAggregateState state) {
        if (!"PENDING".equals(state.getStatus()))
            throw new IllegalStateException("order already decided: " + state.getStatus());
        return new OrderRejectedEvent(cmd.getOrderId(), state.getCustomerId(), cmd.getReason());
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderCreatedEvent e, OrderAggregateState state) {
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
        state.setTotal(e.getTotal());
        return state;
    }

    @EventSourcingHandler
    OrderAggregateState on(OrderRejectedEvent e, OrderAggregateState state) {
        state.setStatus("REJECTED");
        state.setReason(e.getReason());
        return state;
    }
}
