package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flight_view")
public class FlightRow {

    @Id
    private String id;
    private int seatCount;
    private int seatPrice;

    protected FlightRow() {
    }

    public FlightRow(String id, int seatCount, int seatPrice) {
        this.id = id;
        this.seatCount = seatCount;
        this.seatPrice = seatPrice;
    }

    public String getId() {
        return id;
    }

    public int getSeatCount() {
        return seatCount;
    }

    public int getSeatPrice() {
        return seatPrice;
    }
}
