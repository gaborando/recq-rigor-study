package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.study.app.domain.command.CreateProductCommand;
import com.study.app.domain.command.RestockProductCommand;
import com.study.app.domain.query.FindProductQuery;
import com.study.app.domain.view.ProductView;

import java.util.UUID;

@Invoker
public class ProductInvoker extends InvokerWrapper {

    @InvocationHandler
    public ProductView createProduct(String name, int unitPrice, int stock) throws Exception {
        String productId = UUID.randomUUID().toString();
        getCommandGateway().send(new CreateProductCommand(productId, name, unitPrice, stock)).get();
        return new ProductView(productId, name, unitPrice, stock);
    }

    @InvocationHandler
    public ProductView getProduct(String productId) throws Exception {
        return getQueryGateway()
                .<com.evento.common.modeling.messaging.query.Single<ProductView>>query(
                        new FindProductQuery(productId))
                .get()
                .getData();
    }

    @InvocationHandler
    public void restock(String productId, int units) throws Exception {
        getCommandGateway().send(new RestockProductCommand(productId, units)).get();
    }
}
