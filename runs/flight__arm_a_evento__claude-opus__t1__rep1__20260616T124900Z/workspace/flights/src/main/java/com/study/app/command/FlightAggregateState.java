package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

import java.util.HashMap;
import java.util.Map;

/** Event-sourced state of one flight. The status/holder maps are the source of
 *  truth for the seat HOLD lock; commands decide against them, never mutate. */
public class FlightAggregateState extends AggregateState {
    private int seatPrice;
    private Map<String, String> status = new HashMap<>();  // seat -> AVAILABLE/HELD/BOOKED
    private Map<String, String> holder = new HashMap<>();  // seat -> bookingId

    public FlightAggregateState() {}

    public int getSeatPrice() { return seatPrice; }
    public void setSeatPrice(int seatPrice) { this.seatPrice = seatPrice; }
    public Map<String, String> getStatus() { return status; }
    public void setStatus(Map<String, String> status) { this.status = status; }
    public Map<String, String> getHolder() { return holder; }
    public void setHolder(Map<String, String> holder) { this.holder = holder; }

    public boolean hasSeat(String seat) { return status.containsKey(seat); }
    public String statusOf(String seat) { return status.get(seat); }
    public String holderOf(String seat) { return holder.get(seat); }
}
