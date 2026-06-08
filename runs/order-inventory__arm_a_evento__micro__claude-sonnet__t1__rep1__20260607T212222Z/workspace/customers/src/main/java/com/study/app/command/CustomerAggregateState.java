package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

public class CustomerAggregateState extends AggregateState {
    private String name;
    private int balance;

    public CustomerAggregateState() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getBalance() { return balance; }
    public void setBalance(int balance) { this.balance = balance; }
}
