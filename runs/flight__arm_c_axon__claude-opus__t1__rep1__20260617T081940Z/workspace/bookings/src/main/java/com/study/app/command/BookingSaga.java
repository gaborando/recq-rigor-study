package com.study.app.command;

import com.study.app.api.command.BookingCommands.ConfirmBooking;
import com.study.app.api.command.BookingCommands.RejectBooking;
import com.study.app.api.command.CustomerCommands.Charge;
import com.study.app.api.command.FlightCommands.ConfirmSeat;
import com.study.app.api.command.FlightCommands.HoldSeat;
import com.study.app.api.command.FlightCommands.ReleaseSeat;
import com.study.app.api.event.BookingEvents.BookingCreated;
import com.study.app.api.event.CustomerEvents.ChargeRejected;
import com.study.app.api.event.CustomerEvents.Charged;
import com.study.app.api.event.FlightEvents.SeatHeld;
import com.study.app.api.event.FlightEvents.SeatHoldRejected;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.modelling.saga.EndSaga;
import org.axonframework.modelling.saga.SagaEventHandler;
import org.axonframework.modelling.saga.StartSaga;
import org.axonframework.spring.stereotype.Saga;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Booking saga: HOLD the seat (distributed lock) -> CHARGE the customer ->
 * CONFIRM, or compensate (RELEASE the held seat) and REJECT. Each step's command
 * targets the owning aggregate in another service, routed via Axon Server.
 */
@Saga
public class BookingSaga {

    @Autowired
    private transient CommandGateway gateway;

    private String bookingId;
    private String customerId;
    private String flightId;
    private String seat;
    private int price;

    @StartSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(BookingCreated e) {
        this.bookingId = e.bookingId();
        this.customerId = e.customerId();
        this.flightId = e.flightId();
        this.seat = e.seat();
        gateway.send(new HoldSeat(flightId, seat, bookingId));
    }

    @SagaEventHandler(associationProperty = "bookingId")
    public void on(SeatHeld e) {
        this.price = e.seatPrice();
        gateway.send(new Charge(customerId, price, bookingId));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(SeatHoldRejected e) {
        gateway.send(new RejectBooking(bookingId, "SEAT_TAKEN"));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(Charged e) {
        gateway.send(new ConfirmSeat(flightId, seat, bookingId));
        gateway.send(new ConfirmBooking(bookingId, price));
    }

    @EndSaga
    @SagaEventHandler(associationProperty = "bookingId")
    public void on(ChargeRejected e) {
        gateway.send(new ReleaseSeat(flightId, seat, bookingId));   // COMPENSATION
        gateway.send(new RejectBooking(bookingId, "INSUFFICIENT_FUNDS"));
    }
}
