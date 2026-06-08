package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.study.app.domain.command.PlaceOrderCommand;
import com.study.app.domain.query.FindOrderQuery;
import com.study.app.domain.query.GetOrderStatsQuery;
import com.study.app.domain.view.OrderStatsView;
import com.study.app.domain.view.OrderView;

import java.util.concurrent.ExecutionException;

@Invoker
public class OrderInvoker extends InvokerWrapper {

    @InvocationHandler
    public OrderView placeOrder(String orderId, String customerId, String productId, int quantity) throws Exception {
        try {
            getCommandGateway().send(new PlaceOrderCommand(orderId, customerId, productId, quantity)).get();
        } catch (ExecutionException ex) {
            // AggregateInitializedError means duplicate orderId — return current state if available
            try {
                return getQueryGateway()
                        .<com.evento.common.modeling.messaging.query.Single<OrderView>>query(
                                new FindOrderQuery(orderId))
                        .get()
                        .getData();
            } catch (Exception ignored) {
                // Not yet in read model — return synthetic PENDING
            }
            return new OrderView(orderId, customerId, productId, quantity, "PENDING", null, null);
        }
        return new OrderView(orderId, customerId, productId, quantity, "PENDING", null, null);
    }

    @InvocationHandler
    public OrderView getOrder(String orderId) throws Exception {
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Single<OrderView>>query(
                        new FindOrderQuery(orderId))
                .get()
                .getData();
    }

    @InvocationHandler
    public OrderStatsView getStats() throws Exception {
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Single<OrderStatsView>>query(
                        new GetOrderStatsQuery())
                .get()
                .getData();
    }
}
