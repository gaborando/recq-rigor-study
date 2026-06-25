package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_view")
public class ProductViewEntity {
    @Id
    private String id;
    private String name;
    private long unitPrice;
    private long stock;

    public ProductViewEntity() {}
    public ProductViewEntity(String id, String name, long unitPrice, long stock) {
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
