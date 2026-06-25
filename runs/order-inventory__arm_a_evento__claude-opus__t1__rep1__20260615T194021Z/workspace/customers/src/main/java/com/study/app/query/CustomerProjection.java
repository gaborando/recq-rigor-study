package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.CustomerByIdQuery;
import com.study.app.domain.view.CustomerView;

import java.util.NoSuchElementException;

@Projection
public class CustomerProjection {

    private final CustomerViewRepository repository;

    public CustomerProjection(CustomerViewRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<CustomerView> query(CustomerByIdQuery q) {
        var e = repository.findById(q.getCustomerId())
                .orElseThrow(() -> new NoSuchElementException("customer not found: " + q.getCustomerId()));
        return Single.of(new CustomerView(e.getId(), e.getName(), e.getBalance()));
    }
}
