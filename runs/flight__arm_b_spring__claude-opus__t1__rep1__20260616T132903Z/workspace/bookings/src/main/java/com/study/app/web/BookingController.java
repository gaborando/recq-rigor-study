package com.study.app.web;

import com.study.app.command.BookingService;
import com.study.app.command.BookingStore;
import com.study.app.domain.Booking;
import com.study.app.domain.Notification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class BookingController {

    private final BookingService service;
    private final BookingStore store;

    public BookingController(BookingService service, BookingStore store) {
        this.service = service;
        this.store = store;
    }

    @PostMapping("/bookings")
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> body) {
        if (body == null) return ResponseEntity.badRequest().build();
        String bookingId = str(body, "bookingId");
        String customerId = str(body, "customerId");
        String flightId = str(body, "flightId");
        String seat = str(body, "seat");
        if (bookingId == null || customerId == null || flightId == null || seat == null) {
            return ResponseEntity.badRequest().build();
        }
        Booking b = service.book(bookingId, customerId, flightId, seat);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(view(b));
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return store.find(id)
                .<ResponseEntity<?>>map(b -> ResponseEntity.ok(view(b)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/customers/{id}/notifications")
    public ResponseEntity<?> notifications(@PathVariable String id) {
        List<Map<String, Object>> out = store.notificationsFor(id).stream()
                .map(BookingController::notificationView).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/stats/bookings")
    public Map<String, Object> stats() {
        BookingStore.Stats s = store.stats();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("confirmed", s.confirmed());
        m.put("rejected", s.rejected());
        m.put("revenue", s.revenue());
        return m;
    }

    private static Map<String, Object> view(Booking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", b.getBookingId());
        m.put("customerId", b.getCustomerId());
        m.put("flightId", b.getFlightId());
        m.put("seat", b.getSeat());
        m.put("status", b.getStatus());
        if (b.getReason() != null) m.put("reason", b.getReason());
        if (b.getTotal() != null) m.put("total", b.getTotal());
        return m;
    }

    private static Map<String, Object> notificationView(Notification n) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("bookingId", n.getBookingId());
        m.put("status", n.getStatus());
        if (n.getReason() != null) m.put("reason", n.getReason());
        return m;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return (v instanceof String s && !s.isEmpty()) ? s : null;
    }
}
