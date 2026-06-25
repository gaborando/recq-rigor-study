package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.evento.common.modeling.messaging.payload.DomainEvent;
import com.study.app.domain.command.ChargeCommand;
import com.study.app.domain.command.CreateCustomerCommand;
import com.study.app.domain.command.DepositCommand;
import com.study.app.domain.event.ChargeFailedEvent;
import com.study.app.domain.event.ChargedEvent;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.DepositedEvent;

/**
 * Customer aggregate — the consistency boundary for funds. Commands are serialized
 * per customerId by the platform, so funds can never be double-spent or go negative.
 */
@Aggregate
public class CustomerAggregate {

    @AggregateCommandHandler(init = true)
    CustomerCreatedEvent handle(CreateCustomerCommand cmd, CustomerAggregateState state) {
        if (cmd.getName() == null || cmd.getName().isBlank())
            throw new IllegalArgumentException("name is required");
        if (cmd.getBalance() < 0)
            throw new IllegalArgumentException("balance must be >= 0");
        return new CustomerCreatedEvent(cmd.getCustomerId(), cmd.getName(), cmd.getBalance());
    }

    @AggregateCommandHandler
    DepositedEvent handle(DepositCommand cmd, CustomerAggregateState state) {
        if (cmd.getAmount() < 1)
            throw new IllegalArgumentException("amount must be >= 1");
        return new DepositedEvent(cmd.getCustomerId(), cmd.getAmount());
    }

    @AggregateCommandHandler
    DomainEvent handle(ChargeCommand cmd, CustomerAggregateState state) {
        if (state.getBalance() >= cmd.getAmount()) {
            return new ChargedEvent(cmd.getCustomerId(), cmd.getOrderId(), cmd.getAmount());
        }
        return new ChargeFailedEvent(cmd.getCustomerId(), cmd.getOrderId(), cmd.getAmount());
    }

    @EventSourcingHandler
    CustomerAggregateState on(CustomerCreatedEvent e, CustomerAggregateState state) {
        if (state == null) state = new CustomerAggregateState();
        state.setName(e.getName());
        state.setBalance(e.getBalance());
        return state;
    }

    @EventSourcingHandler
    CustomerAggregateState on(DepositedEvent e, CustomerAggregateState state) {
        state.setBalance(state.getBalance() + e.getAmount());
        return state;
    }

    @EventSourcingHandler
    CustomerAggregateState on(ChargedEvent e, CustomerAggregateState state) {
        state.setBalance(state.getBalance() - e.getAmount());
        return state;
    }
}
