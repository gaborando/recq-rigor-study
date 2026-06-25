package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

import java.util.List;

public class FlightCreatedEvent extends DomainEvent {
    private String flightId;
    private int seatCount;
    private int seatPrice;
    private List<String> seatIds;

    public FlightCreatedEvent() {}
    public FlightCreatedEvent(String flightId, int seatCount, int seatPrice, List<String> seatIds) {
        this.flightId = flightId;
        this.seatCount = seatCount;
        this.seatPrice = seatPrice;
        this.seatIds = seatIds;
    }

    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
    public int getSeatPrice() { return seatPrice; }
    public void setSeatPrice(int seatPrice) { this.seatPrice = seatPrice; }
    public List<String> getSeatIds() { return seatIds; }
    public void setSeatIds(List<String> seatIds) { this.seatIds = seatIds; }
}
