package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateFlightCommand extends DomainCommand {
    private String flightId;
    private int seatCount;
    private int seatPrice;

    public CreateFlightCommand() {}
    public CreateFlightCommand(String flightId, int seatCount, int seatPrice) {
        this.flightId = flightId;
        this.seatCount = seatCount;
        this.seatPrice = seatPrice;
    }

    @Override
    public String getAggregateId() { return flightId; }

    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public int getSeatCount() { return seatCount; }
    public void setSeatCount(int seatCount) { this.seatCount = seatCount; }
    public int getSeatPrice() { return seatPrice; }
    public void setSeatPrice(int seatPrice) { this.seatPrice = seatPrice; }
}
