package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

public class ProductAggregateState extends AggregateState {
    private String name;
    private int unitPrice;
    private int stock;

    public ProductAggregateState() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
