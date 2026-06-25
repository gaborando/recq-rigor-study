package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

import java.util.List;

public class FlightView implements View {
    private String id;
    private int seatCount;
    private int seatPrice;
    private List<SeatView> seats;

    public FlightView() {}
    public FlightView(String id, int seatCount, int seatPrice, List<SeatView> seats) {
        this.id = id;
        this.seatCount = seatCount;
        this.seatPrice = seatPrice;
        this.seats = seats;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
    public int getSeatPrice() { return seatPrice; }
    public void setSeatPrice(int seatPrice) { this.seatPrice = seatPrice; }
    public List<SeatView> getSeats() { return seats; }
    public void setSeats(List<SeatView> seats) { this.seats = seats; }
}
