package com.study.app.query;

import com.study.app.api.event.CustomerEvents.Charged;
import com.study.app.api.event.CustomerEvents.CustomerCreated;
import com.study.app.api.event.CustomerEvents.Deposited;
import com.study.app.api.query.Queries.FindCustomer;
import com.study.app.api.query.Views.CustomerView;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("customer-projection")
public class CustomerProjection {

    private final CustomerRowRepository customers;

    public CustomerProjection(CustomerRowRepository customers) {
        this.customers = customers;
    }

    @EventHandler
    public void on(CustomerCreated e) {
        if (!customers.existsById(e.customerId())) {
            customers.save(new CustomerRow(e.customerId(), e.name(), e.balance()));
        }
    }

    @EventHandler
    public void on(Deposited e) {
        customers.findById(e.customerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() + e.amount());
            customers.save(c);
        });
    }

    @EventHandler
    public void on(Charged e) {
        customers.findById(e.customerId()).ifPresent(c -> {
            c.setBalance(c.getBalance() - e.amount());
            customers.save(c);
        });
    }

    @QueryHandler
    public CustomerView handle(FindCustomer q) {
        return customers.findById(q.customerId())
                .map(c -> new CustomerView(c.getId(), c.getName(), c.getBalance()))
                .orElse(null);
    }
}
