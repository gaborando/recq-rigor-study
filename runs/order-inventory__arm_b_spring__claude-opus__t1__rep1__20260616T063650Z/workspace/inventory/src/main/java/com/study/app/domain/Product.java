package com.study.app.domain;

import jakarta.persistence.*;

/**
 * A product with a price and an available-unit counter. {@code available} is the
 * on-hand stock minus anything reserved or confirmed — i.e. the value the public
 * product view reports. Reservation decrements it; release adds it back; confirm
 * leaves it (the reduction is permanent).
 */
@Entity
@Table(name = "product")
public class Product {
    @Id
    @Column(length = 64)
    private String id;
    private String name;
    private int unitPrice;     // cents
    private long available;    // units available to reserve

    protected Product() {}

    public Product(String id, String name, int unitPrice, long available) {
        this.id = id;
        this.name = name;
        this.unitPrice = unitPrice;
        this.available = available;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getUnitPrice() { return unitPrice; }
    public long getAvailable() { return available; }
    public void setAvailable(long available) { this.available = available; }
}
