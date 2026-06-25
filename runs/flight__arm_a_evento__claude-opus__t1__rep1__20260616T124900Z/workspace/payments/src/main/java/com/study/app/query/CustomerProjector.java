package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ChargedEvent;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.DepositedEvent;
import org.springframework.transaction.annotation.Transactional;

@Projector(version = 1)
public class CustomerProjector {

    private final CustomerRepository customers;

    public CustomerProjector(CustomerRepository customers) {
        this.customers = customers;
    }

    @EventHandler
    @Transactional
    void on(CustomerCreatedEvent e) {
        customers.save(new CustomerEntity(e.getCustomerId(), e.getName(), e.getBalance()));
    }

    @EventHandler
    @Transactional
    void on(DepositedEvent e) {
        customers.findById(e.getCustomerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() + e.getAmount());
            customers.save(c);
        });
    }

    @EventHandler
    @Transactional
    void on(ChargedEvent e) {
        customers.findById(e.getCustomerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() - e.getAmount());
            customers.save(c);
        });
    }
}
