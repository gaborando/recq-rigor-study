package com.study.app.command;

import com.evento.common.modeling.annotations.component.Aggregate;
import com.evento.common.modeling.annotations.handler.AggregateCommandHandler;
import com.evento.common.modeling.annotations.handler.EventSourcingHandler;
import com.study.app.domain.command.ChargeCommand;
import com.study.app.domain.command.CreateCustomerCommand;
import com.study.app.domain.command.DepositCommand;
import com.study.app.domain.event.ChargedEvent;
import com.study.app.domain.event.CustomerCreatedEvent;
import com.study.app.domain.event.DepositedEvent;

/**
 * The customer is the consistency boundary for its funds. Per-aggregate command
 * serialization makes deposit (no lost update) and charge (atomic check-and-debit,
 * never negative, never double-spent) safe under concurrency.
 */
@Aggregate
public class CustomerAggregate {

    @AggregateCommandHandler(init = true)
    CustomerCreatedEvent handle(CreateCustomerCommand cmd) {
        if (cmd.getBalance() < 0) throw new IllegalArgumentException("balance < 0");
        return new CustomerCreatedEvent(cmd.getCustomerId(), cmd.getName(), cmd.getBalance());
    }

    @AggregateCommandHandler
    DepositedEvent handle(DepositCommand cmd) {
        if (cmd.getAmount() < 1) throw new IllegalArgumentException("amount < 1");
        return new DepositedEvent(cmd.getCustomerId(), cmd.getAmount());
    }

    @AggregateCommandHandler
    ChargedEvent handle(ChargeCommand cmd, CustomerAggregateState state) {
        if (state.getBalance() < cmd.getAmount()) {
            throw new IllegalStateException("INSUFFICIENT_FUNDS");
        }
        return new ChargedEvent(cmd.getCustomerId(), cmd.getAmount(), cmd.getBookingId());
    }

    @EventSourcingHandler
    CustomerAggregateState on(CustomerCreatedEvent e, CustomerAggregateState state) {
        if (state == null) state = new CustomerAggregateState();
        state.setName(e.getName());
        state.setBalance(e.getBalance());
        return state;
    }

    @EventSourcingHandler
    void on(DepositedEvent e, CustomerAggregateState state) {
        state.setBalance(state.getBalance() + e.getAmount());
    }

    @EventSourcingHandler
    void on(ChargedEvent e, CustomerAggregateState state) {
        state.setBalance(state.getBalance() - e.getAmount());
    }
}
