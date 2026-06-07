package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.command.PlaceOrderCommand;
import com.study.app.domain.query.GetOrderQuery;
import com.study.app.domain.query.GetStatsQuery;
import com.study.app.domain.view.OrderView;
import com.study.app.domain.view.StatsView;

import java.util.concurrent.ExecutionException;

@Invoker
public class OrderInvoker extends InvokerWrapper {

    @InvocationHandler
    public OrderView placeOrder(String orderId, String customerId, String productId, int quantity) throws Exception {
        try {
            getCommandGateway().send(new PlaceOrderCommand(orderId, customerId, productId, quantity)).get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            String causeName = cause == null ? "" : cause.getClass().getSimpleName();
            if (!causeName.contains("Initialized")) {
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                throw e;
            }
            // Idempotent replay — aggregate already exists, continue to return current state
        }
        // Return a synthetic pending view; the client should poll GET /orders/{id} for actual state
        var view = new OrderView();
        view.setOrderId(orderId);
        view.setCustomerId(customerId);
        view.setProductId(productId);
        view.setQuantity(quantity);
        view.setStatus("PENDING");
        return view;
    }

    @InvocationHandler
    public OrderView getOrder(String orderId) throws Exception {
        return getQueryGateway()
                .<Single<OrderView>>query(new GetOrderQuery(orderId))
                .get()
                .getData();
    }

    @InvocationHandler
    public StatsView getStats() throws Exception {
        return getQueryGateway()
                .<Single<StatsView>>query(new GetStatsQuery())
                .get()
                .getData();
    }
}
