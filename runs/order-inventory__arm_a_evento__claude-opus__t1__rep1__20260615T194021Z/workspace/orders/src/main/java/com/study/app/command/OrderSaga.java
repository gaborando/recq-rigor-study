package com.study.app.command;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.study.app.domain.command.ChargeCommand;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.command.RejectOrderCommand;
import com.study.app.domain.command.ReleaseStockCommand;
import com.study.app.domain.command.ReserveStockCommand;
import com.study.app.domain.event.ChargeFailedEvent;
import com.study.app.domain.event.ChargedEvent;
import com.study.app.domain.event.OrderCreatedEvent;
import com.study.app.domain.event.StockReservationFailedEvent;
import com.study.app.domain.event.StockReservedEvent;

/**
 * Coordinates the reserve -> charge -> confirm process across the inventory and
 * customers aggregates, with compensation (release) when funds fall short.
 *
 * Each step is its own checkpointed event handler: the resulting event of one
 * command drives the next handler, so a restart resumes mid-flow without
 * re-issuing already-applied commands (exactly-once across the whole saga).
 */
@Saga(version = 1)
public class OrderSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId", retry = 10, retryDelay = 500)
    OrderSagaState on(OrderCreatedEvent e, CommandGateway commandGateway) throws Exception {
        var state = new OrderSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        state.setCustomerId(e.getCustomerId());
        state.setProductId(e.getProductId());
        state.setQuantity(e.getQuantity());
        commandGateway.send(new ReserveStockCommand(e.getProductId(), e.getOrderId(), e.getQuantity())).get();
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId", retry = 10, retryDelay = 500)
    OrderSagaState on(StockReservedEvent e, OrderSagaState state, CommandGateway commandGateway) throws Exception {
        long total = (long) e.getQuantity() * e.getUnitPrice();
        state.setTotal(total);
        commandGateway.send(new ChargeCommand(state.getCustomerId(), e.getOrderId(), total)).get();
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId", retry = 10, retryDelay = 500)
    OrderSagaState on(StockReservationFailedEvent e, OrderSagaState state, CommandGateway commandGateway) throws Exception {
        commandGateway.send(new RejectOrderCommand(e.getOrderId(), "OUT_OF_STOCK")).get();
        state.setEnded(true);
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId", retry = 10, retryDelay = 500)
    OrderSagaState on(ChargedEvent e, OrderSagaState state, CommandGateway commandGateway) throws Exception {
        commandGateway.send(new ConfirmOrderCommand(e.getOrderId(), e.getAmount())).get();
        state.setEnded(true);
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId", retry = 10, retryDelay = 500)
    OrderSagaState on(ChargeFailedEvent e, OrderSagaState state, CommandGateway commandGateway) throws Exception {
        // compensation: release the reservation made during processing, then reject
        commandGateway.send(new ReleaseStockCommand(state.getProductId(), e.getOrderId(), state.getQuantity())).get();
        commandGateway.send(new RejectOrderCommand(e.getOrderId(), "INSUFFICIENT_FUNDS")).get();
        state.setEnded(true);
        return state;
    }
}
