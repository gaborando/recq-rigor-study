package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class ProductView implements View {
    private String id;
    private String name;
    private int unitPrice;
    private int stock;

    public ProductView() {}

    public ProductView(String id, String name, int unitPrice, int stock) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getUnitPrice() { return unitPrice; }
    public void setUnitPrice(int unitPrice) { this.unitPrice = unitPrice; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
