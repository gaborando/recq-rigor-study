package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_view")
public class ProductView {
    @Id
    private String id;
    private String name;
    private int unitPrice;
    private int stock;

    protected ProductView() {}

    public ProductView(String id, String name, int unitPrice, int stock) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getUnitPrice() { return unitPrice; }
    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }
}
