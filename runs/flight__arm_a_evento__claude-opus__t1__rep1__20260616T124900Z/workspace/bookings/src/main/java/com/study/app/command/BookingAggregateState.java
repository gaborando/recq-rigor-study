package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

public class BookingAggregateState extends AggregateState {
    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String REJECTED = "REJECTED";

    private String customerId;
    private String flightId;
    private String seat;
    private String status;

    public BookingAggregateState() {}

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getFlightId() { return flightId; }
    public void setFlightId(String flightId) { this.flightId = flightId; }
    public String getSeat() { return seat; }
    public void setSeat(String seat) { this.seat = seat; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
