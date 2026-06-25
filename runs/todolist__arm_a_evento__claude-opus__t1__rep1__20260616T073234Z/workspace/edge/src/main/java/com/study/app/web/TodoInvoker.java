package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.command.AddItemCommand;
import com.study.app.domain.command.CheckItemCommand;
import com.study.app.domain.command.CreateListCommand;
import com.study.app.domain.command.RenameItemCommand;
import com.study.app.domain.command.UncheckItemCommand;
import com.study.app.domain.query.GetListQuery;
import com.study.app.domain.query.GetNotificationsQuery;
import com.study.app.domain.query.GetStatsQuery;
import com.study.app.domain.view.ListView;
import com.study.app.domain.view.NotificationView;
import com.study.app.domain.view.StatsView;

import java.util.Collection;
import java.util.concurrent.ExecutionException;

/**
 * The only place commands/queries originate. The controller delegates here; each
 * handler blocks on the gateway future to turn the async result into an HTTP response.
 */
@Invoker
public class TodoInvoker extends InvokerWrapper {

    @InvocationHandler
    public void createList(String listId, String name) throws Exception {
        try {
            getCommandGateway().send(new CreateListCommand(listId, name)).get();
        } catch (ExecutionException ex) {
            // Re-creating the same listId is idempotent: never a second list, never a reset.
            if (EventoErrors.isAlreadyInitialized(ex)) return;
            throw ex;
        }
    }

    @InvocationHandler
    public void addItem(String listId, String itemId, String content) throws Exception {
        getCommandGateway().send(new AddItemCommand(listId, itemId, content)).get();
    }

    @InvocationHandler
    public void checkItem(String listId, String itemId) throws Exception {
        getCommandGateway().send(new CheckItemCommand(listId, itemId)).get();
    }

    @InvocationHandler
    public void uncheckItem(String listId, String itemId) throws Exception {
        getCommandGateway().send(new UncheckItemCommand(listId, itemId)).get();
    }

    @InvocationHandler
    public void renameItem(String listId, String itemId, String content) throws Exception {
        getCommandGateway().send(new RenameItemCommand(listId, itemId, content)).get();
    }

    @InvocationHandler
    public ListView getList(String listId) throws Exception {
        Single<ListView> r = getQueryGateway().query(new GetListQuery(listId)).get();
        return r.getData();
    }

    @InvocationHandler
    public StatsView getStats() throws Exception {
        Single<StatsView> r = getQueryGateway().query(new GetStatsQuery()).get();
        return r.getData();
    }

    @InvocationHandler
    public Collection<NotificationView> getNotifications(String listId) throws Exception {
        Multiple<NotificationView> r = getQueryGateway().query(new GetNotificationsQuery(listId)).get();
        return r.getData();
    }
}
