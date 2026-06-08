package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.ChargeCustomerCommand;
import com.study.app.domain.command.CreateCustomerCommand;
import com.study.app.domain.command.DepositFundsCommand;
import com.study.app.domain.event.CustomerChargedEvent;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.FundsDepositedEvent;

@Aggregate(snapshotFrequency = 50)
public class CustomerAggregate {

    @AggregateCommandHandler(init = true)
    CustomerCreatedEvent handle(CreateCustomerCommand cmd, CustomerAggregateState state) {
        if (cmd.getName() == null || cmd.getName().isBlank())
            throw new IllegalArgumentException("name required");
        if (cmd.getBalance() < 0)
            throw new IllegalArgumentException("balance must be >= 0");
        return new CustomerCreatedEvent(cmd.getCustomerId(), cmd.getName(), cmd.getBalance());
    }

    @AggregateCommandHandler
    FundsDepositedEvent handle(DepositFundsCommand cmd, CustomerAggregateState state) {
        if (cmd.getAmount() < 1)
            throw new IllegalArgumentException("amount must be >= 1");
        return new FundsDepositedEvent(cmd.getCustomerId(), cmd.getAmount());
    }

    @AggregateCommandHandler
    CustomerChargedEvent handle(ChargeCustomerCommand cmd, CustomerAggregateState state) {
        if (state.getBalance() < cmd.getAmount())
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        return new CustomerChargedEvent(cmd.getCustomerId(), cmd.getOrderId(), cmd.getAmount());
    }

    @EventSourcingHandler
    CustomerAggregateState on(CustomerCreatedEvent e, CustomerAggregateState state) {
        if (state == null) state = new CustomerAggregateState();
        state.setName(e.getName());
        state.setBalance(e.getBalance());
        return state;
    }

    @EventSourcingHandler
    CustomerAggregateState on(FundsDepositedEvent e, CustomerAggregateState state) {
        state.setBalance(state.getBalance() + e.getAmount());
        return state;
    }

    @EventSourcingHandler
    CustomerAggregateState on(CustomerChargedEvent e, CustomerAggregateState state) {
        state.setBalance(state.getBalance() - e.getAmount());
        return state;
    }
}
