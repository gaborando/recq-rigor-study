package com.study.app.command;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.study.app.domain.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Process manager coordinating: reserve stock -> charge -> confirm,
 * with compensation (release stock) when the charge fails.
 */
@Saga
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class OrderSaga {

    @JsonIgnore
    @Autowired
    private transient CommandGateway commandGateway;

    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private int total;

    @StartSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderCreatedEvent e) {
        this.orderId = e.orderId();
        this.customerId = e.customerId();
        this.productId = e.productId();
        this.quantity = e.quantity();
        commandGateway.send(new com.study.app.command.ReserveStockCommand(productId, orderId, quantity));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(StockReservedEvent e) {
        this.total = e.total();
        commandGateway.send(new com.study.app.command.ChargeCommand(customerId, orderId, e.total()));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(StockReservationFailedEvent e) {
        commandGateway.send(new com.study.app.command.RejectOrderCommand(orderId, "OUT_OF_STOCK"));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(ChargedEvent e) {
        commandGateway.send(new com.study.app.command.ConfirmOrderCommand(orderId, total));
    }

    @SagaEventHandler(associationProperty = "orderId")
    public void on(ChargeFailedEvent e) {
        // compensation: release the reservation, then reject
        commandGateway.send(new com.study.app.command.ReleaseStockCommand(productId, orderId, quantity));
        commandGateway.send(new com.study.app.command.RejectOrderCommand(orderId, "INSUFFICIENT_FUNDS"));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderConfirmedEvent e) {
        // terminal
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "orderId")
    public void on(OrderRejectedEvent e) {
        // terminal
    }
}
