package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ChargeFailedEvent;
import com.study.app.domain.event.CustomerChargedEvent;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.FundsDepositedEvent;
import com.study.app.query.entity.CustomerEntity;
import com.study.app.query.repository.CustomerRepository;

@Projector(version = 1)
public class CustomerProjector {

    private final CustomerRepository customerRepository;

    public CustomerProjector(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @EventHandler
    void on(CustomerCreatedEvent e) {
        var entity = new CustomerEntity(e.getCustomerId(), e.getName(), e.getBalance());
        customerRepository.save(entity);
    }

    @EventHandler
    void on(FundsDepositedEvent e) {
        customerRepository.findById(e.getCustomerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() + e.getAmount());
            customerRepository.save(c);
        });
    }

    @EventHandler
    void on(CustomerChargedEvent e) {
        customerRepository.findById(e.getCustomerId()).ifPresent(c -> {
            c.setBalance((int) (c.getBalance() - e.getAmount()));
            customerRepository.save(c);
        });
    }

    @EventHandler
    void on(ChargeFailedEvent e) {
        // no balance change
    }
}
