package com.study.app.command;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.study.app.domain.command.ChargeCustomerCommand;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.command.RejectOrderCommand;
import com.study.app.domain.command.ReleaseStockCommand;
import com.study.app.domain.command.ReserveStockCommand;
import com.study.app.domain.event.ChargeFailedEvent;
import com.study.app.domain.event.CustomerChargedEvent;
import com.study.app.domain.event.OrderPlacedEvent;
import com.study.app.domain.event.StockReservationFailedEvent;
import com.study.app.domain.event.StockReservedEvent;

@Saga(version = 1)
public class OrderSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    OrderSagaState on(OrderPlacedEvent e, CommandGateway cg) throws Exception {
        OrderSagaState state = new OrderSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        state.setCustomerId(e.getCustomerId());
        state.setProductId(e.getProductId());
        state.setQuantity(e.getQuantity());
        cg.send(new ReserveStockCommand(e.getProductId(), e.getOrderId(), e.getQuantity())).get();
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(StockReservedEvent e, OrderSagaState state, CommandGateway cg) throws Exception {
        cg.send(new ChargeCustomerCommand(state.getCustomerId(), e.getOrderId(), e.getTotalAmount())).get();
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(StockReservationFailedEvent e, OrderSagaState state, CommandGateway cg) throws Exception {
        cg.send(new RejectOrderCommand(state.getOrderId(), "OUT_OF_STOCK")).get();
        state.setEnded(true);
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(CustomerChargedEvent e, OrderSagaState state, CommandGateway cg) throws Exception {
        cg.send(new ConfirmOrderCommand(state.getOrderId(), e.getAmount())).get();
        state.setEnded(true);
        return state;
    }

    @SagaEventHandler(associationProperty = "orderId")
    OrderSagaState on(ChargeFailedEvent e, OrderSagaState state, CommandGateway cg) throws Exception {
        cg.send(new ReleaseStockCommand(state.getProductId(), state.getOrderId(), state.getQuantity())).get();
        cg.send(new RejectOrderCommand(state.getOrderId(), "INSUFFICIENT_FUNDS")).get();
        state.setEnded(true);
        return state;
    }
}
