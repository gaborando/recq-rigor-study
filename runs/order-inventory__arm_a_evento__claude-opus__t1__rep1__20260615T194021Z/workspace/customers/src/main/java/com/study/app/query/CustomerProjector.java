package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ChargedEvent;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.DepositedEvent;

/**
 * Builds the customer read model. Balance mirrors the aggregate: deposits add,
 * charges subtract. Ordered single-active consumption keeps it consistent.
 */
@Projector(version = 1)
public class CustomerProjector {

    private final CustomerViewRepository repository;

    public CustomerProjector(CustomerViewRepository repository) {
        this.repository = repository;
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(CustomerCreatedEvent e) {
        repository.save(new CustomerViewEntity(e.getCustomerId(), e.getName(), e.getBalance()));
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(DepositedEvent e) {
        repository.adjustBalance(e.getCustomerId(), e.getAmount());
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(ChargedEvent e) {
        repository.adjustBalance(e.getCustomerId(), -e.getAmount());
    }
}
