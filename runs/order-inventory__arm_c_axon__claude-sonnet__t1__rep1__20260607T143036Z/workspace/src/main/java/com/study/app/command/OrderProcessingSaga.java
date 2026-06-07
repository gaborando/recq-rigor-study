package com.study.app.command;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.study.app.domain.events.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

@Saga
@ProcessingGroup("order-saga")
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OrderProcessingSaga {

    @JsonIgnore
    @Autowired
    private transient CommandGateway commandGateway;

    private String orderId;
    private String productId;
    private String customerId;
    private int quantity;
    private int total;

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent e) {
        orderId = e.orderId();
        productId = e.productId();
        customerId = e.customerId();
        quantity = e.quantity();

        commandGateway.send(new ReserveStockCommand(productId, orderId, customerId, quantity));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(StockReservedEvent e) {
        total = e.total();
        commandGateway.send(new ChargeFundsCommand(customerId, orderId, productId, quantity, total));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(StockReservationFailedEvent e) {
        commandGateway.send(new RejectOrderCommand(orderId, "OUT_OF_STOCK"));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(FundsChargedEvent e) {
        commandGateway.send(new ConfirmOrderCommand(orderId, total));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(ChargeFailedEvent e) {
        commandGateway.send(new ReleaseStockCommand(productId, orderId, quantity));
        commandGateway.send(new RejectOrderCommand(orderId, "INSUFFICIENT_FUNDS"));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderConfirmedEvent e) {}

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderRejectedEvent e) {}
}
