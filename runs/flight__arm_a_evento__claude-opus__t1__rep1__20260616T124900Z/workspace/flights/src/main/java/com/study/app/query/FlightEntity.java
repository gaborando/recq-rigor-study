package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flight")
public class FlightEntity {
    @Id
    private String id;
    private int seatCount;
    private int seatPrice;

    public FlightEntity() {}
    public FlightEntity(String id, int seatCount, int seatPrice) {
        this.id = id;
        this.seatCount = seatCount;
        this.seatPrice = seatPrice;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
    public int getSeatPrice() { return seatPrice; }
    public void setSeatPrice(int seatPrice) { this.seatPrice = seatPrice; }
}
