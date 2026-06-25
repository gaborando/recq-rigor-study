package com.study.app.web;

import com.evento.application.proxy.InvokerWrapper;
import com.evento.common.modeling.annotations.component.Invoker;
import com.evento.common.modeling.annotations.handler.InvocationHandler;
import com.evento.common.modeling.exceptions.AggregateInitializedError;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.SeatIds;
import com.study.app.domain.command.CreateBookingCommand;
import com.study.app.domain.command.CreateCustomerCommand;
import com.study.app.domain.command.CreateFlightCommand;
import com.study.app.domain.command.DepositCommand;
import com.study.app.domain.query.GetBookingQuery;
import com.study.app.domain.query.GetCustomerQuery;
import com.study.app.domain.query.GetFlightQuery;
import com.study.app.domain.query.GetNotificationsQuery;
import com.study.app.domain.query.GetStatsQuery;
import com.study.app.domain.view.BookingView;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.FlightView;
import com.study.app.domain.view.NotificationView;
import com.study.app.domain.view.SeatView;
import com.study.app.domain.view.StatsView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * The only component that originates commands/queries onto the bus. The Evento
 * server routes each message to the bundle that owns the handler — edge holds no
 * domain state and no database.
 */
@Invoker
public class ApiInvoker extends InvokerWrapper {

    // ---- flights ----

    @InvocationHandler
    public FlightView createFlight(int seatCount, int seatPrice) throws Exception {
        String flightId = UUID.randomUUID().toString();
        getCommandGateway().send(new CreateFlightCommand(flightId, seatCount, seatPrice)).get();
        List<SeatView> seats = new ArrayList<>();
        for (String s : SeatIds.generate(seatCount)) {
            seats.add(new SeatView(s, true, null));
        }
        return new FlightView(flightId, seatCount, seatPrice, seats);
    }

    @InvocationHandler
    public FlightView getFlight(String flightId) throws Exception {
        return getQueryGateway().<Single<FlightView>>query(new GetFlightQuery(flightId)).get().getData();
    }

    // ---- customers ----

    @InvocationHandler
    public CustomerView createCustomer(String name, long balance) throws Exception {
        String customerId = UUID.randomUUID().toString();
        getCommandGateway().send(new CreateCustomerCommand(customerId, name, balance)).get();
        return new CustomerView(customerId, name, balance);
    }

    @InvocationHandler
    public CustomerView getCustomer(String customerId) throws Exception {
        return getQueryGateway().<Single<CustomerView>>query(new GetCustomerQuery(customerId)).get().getData();
    }

    @InvocationHandler
    public void deposit(String customerId, long amount) throws Exception {
        getCommandGateway().send(new DepositCommand(customerId, amount)).get();
    }

    @InvocationHandler
    public Collection<NotificationView> notifications(String customerId) throws Exception {
        return getQueryGateway().<Multiple<NotificationView>>query(
                new GetNotificationsQuery(customerId)).get().getData();
    }

    // ---- bookings ----

    @InvocationHandler
    public BookingView createBooking(String bookingId, String customerId, String flightId, String seat)
            throws Exception {
        try {
            getCommandGateway().send(new CreateBookingCommand(bookingId, customerId, flightId, seat)).get();
        } catch (Exception ex) {
            if (!isAlreadyCreated(ex)) throw ex; // genuine failure
            // idempotent replay: the booking already exists, fall through.
        }
        BookingView v = new BookingView();
        v.setBookingId(bookingId);
        v.setCustomerId(customerId);
        v.setFlightId(flightId);
        v.setSeat(seat);
        v.setStatus("PENDING");
        return v;
    }

    @InvocationHandler
    public BookingView getBooking(String bookingId) throws Exception {
        return getQueryGateway().<Single<BookingView>>query(new GetBookingQuery(bookingId)).get().getData();
    }

    // ---- stats ----

    @InvocationHandler
    public StatsView stats() throws Exception {
        return getQueryGateway().<Single<StatsView>>query(new GetStatsQuery()).get().getData();
    }

    private static boolean isAlreadyCreated(Throwable t) {
        Throwable c = t;
        while (c != null) {
            if (c instanceof AggregateInitializedError) return true;
            String n = c.getClass().getName();
            if (n.contains("AggregateInitializedError")) return true;
            if (c.getMessage() != null && c.getMessage().contains("AggregateInitializedError")) return true;
            if (c == c.getCause()) break;
            c = c.getCause();
        }
        return false;
    }
}
