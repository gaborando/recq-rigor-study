package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.CustomerChargedEvent;
import com.study.app.domain.event.FundsDepositedEvent;

@Projector(version = 1)
public class CustomerProjector {

    private final CustomerRepository repository;

    public CustomerProjector(CustomerRepository repository) {
        this.repository = repository;
    }

    @EventHandler
    void on(CustomerCreatedEvent e) {
        repository.save(new CustomerEntity(e.getCustomerId(), e.getName(), e.getBalance()));
    }

    @EventHandler
    void on(FundsDepositedEvent e) {
        repository.findById(e.getCustomerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() + e.getAmount());
            repository.save(c);
        });
    }

    @EventHandler
    void on(CustomerChargedEvent e) {
        repository.findById(e.getCustomerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() - e.getAmount());
            repository.save(c);
        });
    }
}
