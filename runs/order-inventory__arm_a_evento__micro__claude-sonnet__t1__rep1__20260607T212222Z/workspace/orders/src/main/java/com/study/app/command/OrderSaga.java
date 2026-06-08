package com.study.app.command;

import com.evento.common.messaging.gateway.CommandGateway;
import com.evento.common.modeling.annotations.component.Saga;
import com.evento.common.modeling.annotations.handler.SagaEventHandler;
import com.study.app.domain.command.ChargeCustomerCommand;
import com.study.app.domain.command.ConfirmOrderCommand;
import com.study.app.domain.command.RejectOrderCommand;
import com.study.app.domain.command.ReleaseStockCommand;
import com.study.app.domain.command.ReserveStockCommand;
import com.study.app.domain.event.OrderPlacedEvent;
import com.study.app.domain.event.StockReservedEvent;

import java.util.concurrent.ExecutionException;

@Saga(version = 1)
public class OrderSaga {

    @SagaEventHandler(init = true, associationProperty = "orderId")
    OrderSagaState on(OrderPlacedEvent e, CommandGateway cg) throws Exception {
        var state = new OrderSagaState();
        state.setAssociation("orderId", e.getOrderId());
        state.setOrderId(e.getOrderId());
        state.setCustomerId(e.getCustomerId());
        state.setProductId(e.getProductId());
        state.setQuantity(e.getQuantity());

        StockReservedEvent reserved;
        try {
            reserved = (StockReservedEvent) cg.send(
                    new ReserveStockCommand(e.getProductId(), e.getOrderId(), e.getQuantity())).get();
        } catch (ExecutionException ex) {
            cg.send(new RejectOrderCommand(e.getOrderId(), "OUT_OF_STOCK")).get();
            state.setEnded(true);
            return state;
        }

        int total = reserved.getUnitPrice() * e.getQuantity();

        try {
            cg.send(new ChargeCustomerCommand(e.getCustomerId(), e.getOrderId(), total)).get();
        } catch (ExecutionException ex) {
            cg.send(new ReleaseStockCommand(e.getProductId(), e.getOrderId(), e.getQuantity())).get();
            cg.send(new RejectOrderCommand(e.getOrderId(), "INSUFFICIENT_FUNDS")).get();
            state.setEnded(true);
            return state;
        }

        cg.send(new ConfirmOrderCommand(e.getOrderId(), total)).get();
        state.setEnded(true);
        return state;
    }
}
