package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class ProductView implements View {
    private String id;
    private String name;
    private long unitPrice;
    private long stock;

    public ProductView() {}
    public ProductView(String id, String name, long unitPrice, long stock) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public long getUnitPrice() { return unitPrice; }
    public void setUnitPrice(long unitPrice) { this.unitPrice = unitPrice; }
    public long getStock() { return stock; }
    public void setStock(long stock) { this.stock = stock; }
}
