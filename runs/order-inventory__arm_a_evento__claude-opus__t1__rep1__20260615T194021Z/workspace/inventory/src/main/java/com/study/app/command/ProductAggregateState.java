package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

public class ProductAggregateState extends AggregateState {
    private String name;
    private long unitPrice;
    private long available;   // units on hand minus reserved/confirmed

    public ProductAggregateState() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }
    public long getAvailable() { return available; }
    public void setAvailable(long available) { this.available = available; }
}
