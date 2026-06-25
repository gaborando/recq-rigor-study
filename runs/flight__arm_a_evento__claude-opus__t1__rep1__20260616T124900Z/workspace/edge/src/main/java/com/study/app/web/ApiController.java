package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.BookingView;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.FlightView;
import com.study.app.domain.view.NotificationView;
import com.study.app.domain.view.StatsView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;

@RestController
public class ApiController {

    private final ApiInvoker invoker;

    public ApiController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(ApiInvoker.class);
    }

    // ---------------- flights ----------------

    @PostMapping("/flights")
    public ResponseEntity<FlightView> createFlight(@RequestBody Map<String, Object> body) throws Exception {
        Integer seatCount = asInt(body.get("seatCount"));
        Integer seatPrice = asInt(body.get("seatPrice"));
        if (seatCount == null || seatPrice == null) throw badRequest("seatCount and seatPrice are required");
        if (seatCount < 1) throw badRequest("seatCount < 1");
        if (seatPrice < 1) throw badRequest("seatPrice < 1");
        return ResponseEntity.status(HttpStatus.CREATED).body(invoker.createFlight(seatCount, seatPrice));
    }

    @GetMapping("/flights/{id}")
    public FlightView getFlight(@PathVariable String id) throws Exception {
        return invoker.getFlight(id);
    }

    // ---------------- customers ----------------

    @PostMapping("/customers")
    public ResponseEntity<CustomerView> createCustomer(@RequestBody Map<String, Object> body) throws Exception {
        Object nameObj = body.get("name");
        Long balance = asLong(body.get("balance"));
        if (nameObj == null || nameObj.toString().isBlank()) throw badRequest("name is required");
        if (balance == null) throw badRequest("balance is required");
        if (balance < 0) throw badRequest("balance < 0");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(invoker.createCustomer(nameObj.toString(), balance));
    }

    @GetMapping("/customers/{id}")
    public CustomerView getCustomer(@PathVariable String id) throws Exception {
        return invoker.getCustomer(id);
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<Void> deposit(@PathVariable String id, @RequestBody Map<String, Object> body)
            throws Exception {
        Long amount = asLong(body.get("amount"));
        if (amount == null) throw badRequest("amount is required");
        if (amount < 1) throw badRequest("amount < 1");
        invoker.deposit(id, amount);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }

    @GetMapping("/customers/{id}/notifications")
    public Collection<NotificationView> notifications(@PathVariable String id) throws Exception {
        return invoker.notifications(id);
    }

    // ---------------- bookings ----------------

    @PostMapping("/bookings")
    public ResponseEntity<BookingView> createBooking(@RequestBody Map<String, Object> body) throws Exception {
        String bookingId = asStr(body.get("bookingId"));
        String customerId = asStr(body.get("customerId"));
        String flightId = asStr(body.get("flightId"));
        String seat = asStr(body.get("seat"));
        if (bookingId == null || customerId == null || flightId == null || seat == null) {
            throw badRequest("bookingId, customerId, flightId and seat are required");
        }
        BookingView v = invoker.createBooking(bookingId, customerId, flightId, seat);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(v);
    }

    @GetMapping("/bookings/{id}")
    public BookingView getBooking(@PathVariable String id) throws Exception {
        return invoker.getBooking(id);
    }

    // ---------------- stats ----------------

    @GetMapping("/stats/bookings")
    public StatsView stats() throws Exception {
        return invoker.stats();
    }

    // ---------------- helpers ----------------

    private static ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private static String asStr(Object o) {
        if (o == null) return null;
        String s = o.toString();
        return s.isBlank() ? null : s;
    }

    private static Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s && !s.isBlank()) {
            try { return Integer.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        if (o instanceof String s && !s.isBlank()) {
            try { return Long.valueOf(s.trim()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
