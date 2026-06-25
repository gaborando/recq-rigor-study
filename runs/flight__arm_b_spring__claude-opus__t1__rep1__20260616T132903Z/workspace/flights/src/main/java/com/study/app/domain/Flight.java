package com.study.app.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flights")
public class Flight {
    @Id
    private String id;
    private int seatCount;
    private int seatPrice;

    protected Flight() {}

    public Flight(String id, int seatCount, int seatPrice) {
        this.id = id;
        this.seatCount = seatCount;
        this.seatPrice = seatPrice;
    }

    public String getId() { return id; }
    public int getSeatCount() { return seatCount; }
    public int getSeatPrice() { return seatPrice; }
}
