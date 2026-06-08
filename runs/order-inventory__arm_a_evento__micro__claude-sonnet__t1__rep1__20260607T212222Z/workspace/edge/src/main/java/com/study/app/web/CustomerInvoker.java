package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.study.app.domain.command.CreateCustomerCommand;
import com.study.app.domain.command.DepositFundsCommand;
import com.study.app.domain.query.FindCustomerNotificationsQuery;
import com.study.app.domain.query.FindCustomerQuery;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Invoker
public class CustomerInvoker extends InvokerWrapper {

    @InvocationHandler
    public CustomerView createCustomer(String name, int balance) throws Exception {
        String customerId = UUID.randomUUID().toString();
        getCommandGateway().send(new CreateCustomerCommand(customerId, name, balance)).get();
        return new CustomerView(customerId, name, balance);
    }

    @InvocationHandler
    public CustomerView getCustomer(String customerId) throws Exception {
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Single<CustomerView>>query(
                        new FindCustomerQuery(customerId))
                .get()
                .getData();
    }

    @InvocationHandler
    public void deposit(String customerId, int amount) throws Exception {
        getCommandGateway().send(new DepositFundsCommand(customerId, amount)).get();
    }

    @InvocationHandler
    public Collection<NotificationView> getNotifications(String customerId) throws Exception {
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Multiple<NotificationView>>query(
                        new FindCustomerNotificationsQuery(customerId))
                .get()
                .getData();
    }
}
