package com.study.app.query;

import com.study.app.domain.events.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("customer-projection")
public class CustomerProjection {

    private final CustomerViewRepository repo;

    public CustomerProjection(CustomerViewRepository repo) {
        this.repo = repo;
    }

    @EventHandler
    public void on(CustomerCreatedEvent e) {
        repo.save(new CustomerView(e.customerId(), e.name(), e.balance()));
    }

    @EventHandler
    public void on(FundsDepositedEvent e) {
        repo.findById(e.customerId()).ifPresent(v -> {
            v.setBalance(v.getBalance() + e.amount());
            repo.save(v);
        });
    }

    @EventHandler
    public void on(FundsChargedEvent e) {
        repo.findById(e.customerId()).ifPresent(v -> {
            v.setBalance(v.getBalance() - e.amount());
            repo.save(v);
        });
    }

    @QueryHandler
    public CustomerView handle(FindCustomer q) {
        return repo.findById(q.customerId()).orElse(null);
    }
}
