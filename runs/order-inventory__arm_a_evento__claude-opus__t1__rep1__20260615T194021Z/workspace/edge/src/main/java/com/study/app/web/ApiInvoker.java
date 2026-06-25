package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.command.CreateCustomerCommand;
import com.study.app.domain.command.CreateOrderCommand;
import com.study.app.domain.command.CreateProductCommand;
import com.study.app.domain.command.DepositCommand;
import com.study.app.domain.command.RestockProductCommand;
import com.study.app.domain.query.CustomerByIdQuery;
import com.study.app.domain.query.NotificationsByCustomerQuery;
import com.study.app.domain.query.OrderByIdQuery;
import com.study.app.domain.query.OrderStatsQuery;
import com.study.app.domain.query.ProductByIdQuery;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;
import com.study.app.domain.view.OrderView;
import com.study.app.domain.view.ProductView;
import com.study.app.domain.view.StatsView;

import java.util.Collection;

/**
 * The only place gateway calls originate in the stateless edge bundle. All
 * methods block on the async gateway futures to turn them into HTTP responses.
 */
@Invoker
public class ApiInvoker extends InvokerWrapper {

    // ----- products -----

    @InvocationHandler
    public void createProduct(String productId, String name, long unitPrice, long stock) throws Exception {
        getCommandGateway().send(new CreateProductCommand(productId, name, unitPrice, stock)).get();
    }

    @InvocationHandler
    public void restock(String productId, long units) throws Exception {
        getCommandGateway().send(new RestockProductCommand(productId, units)).get();
    }

    @InvocationHandler
    public ProductView getProduct(String productId) throws Exception {
        return getQueryGateway()
                .<Single<ProductView>>query(new ProductByIdQuery(productId))
                .get().getData();
    }

    // ----- customers -----

    @InvocationHandler
    public void createCustomer(String customerId, String name, long balance) throws Exception {
        getCommandGateway().send(new CreateCustomerCommand(customerId, name, balance)).get();
    }

    @InvocationHandler
    public void deposit(String customerId, long amount) throws Exception {
        getCommandGateway().send(new DepositCommand(customerId, amount)).get();
    }

    @InvocationHandler
    public CustomerView getCustomer(String customerId) throws Exception {
        return getQueryGateway()
                .<Single<CustomerView>>query(new CustomerByIdQuery(customerId))
                .get().getData();
    }

    @InvocationHandler
    public Collection<NotificationView> getNotifications(String customerId) throws Exception {
        return getQueryGateway()
                .<Multiple<NotificationView>>query(new NotificationsByCustomerQuery(customerId))
                .get().getData();
    }

    // ----- orders -----

    @InvocationHandler
    public void createOrder(String orderId, String customerId, String productId, int quantity) throws Exception {
        getCommandGateway().send(new CreateOrderCommand(orderId, customerId, productId, quantity)).get();
    }

    @InvocationHandler
    public OrderView getOrder(String orderId) throws Exception {
        return getQueryGateway()
                .<Single<OrderView>>query(new OrderByIdQuery(orderId))
                .get().getData();
    }

    // ----- stats -----

    @InvocationHandler
    public StatsView getStats() throws Exception {
        return getQueryGateway()
                .<Single<StatsView>>query(new OrderStatsQuery())
                .get().getData();
    }
}
