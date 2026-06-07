package com.study.app.query.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_view")
public class ProductEntity {
    @Id
    private String id;
    private String name;
    private int unitPrice;
    private int stock;

    public ProductEntity() {}

    public ProductEntity(String id, String name, int unitPrice, int stock) {
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
