package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false)
    private int unitPrice;

    @Column(nullable = false)
    private int stock;

    @Version
    private long version;

    protected Product() {}

    public Product(String name, int unitPrice, int stock) {
        this.name = name;
        this.unitPrice = unitPrice;
        this.stock = stock;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public int getUnitPrice() { return unitPrice; }
    public int getStock() { return stock; }
}
