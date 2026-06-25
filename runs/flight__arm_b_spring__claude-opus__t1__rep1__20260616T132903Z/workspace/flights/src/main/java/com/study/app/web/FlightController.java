package com.study.app.web;

import com.study.app.command.FlightService;
import com.study.app.domain.Flight;
import com.study.app.domain.Seat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class FlightController {

    private final FlightService service;

    public FlightController(FlightService service) {
        this.service = service;
    }

    @PostMapping("/flights")
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> body) {
        Integer seatCount = intOrNull(body, "seatCount");
        Integer seatPrice = intOrNull(body, "seatPrice");
        if (seatCount == null || seatPrice == null || seatCount < 1 || seatPrice < 1) {
            return ResponseEntity.badRequest().build();
        }
        Flight f = service.createFlight(seatCount, seatPrice);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(view(f, service.getSeats(f.getId())));
    }

    @GetMapping("/flights/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        return service.getFlight(id)
                .<ResponseEntity<?>>map(f -> ResponseEntity.ok(view(f, service.getSeats(id))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- internal saga endpoints (called by the bookings service) ----

    @PostMapping("/internal/hold")
    public Map<String, Object> hold(@RequestBody Map<String, String> body) {
        FlightService.Hold h = service.hold(body.get("flightId"), body.get("seat"), body.get("bookingId"));
        return Map.of("result", h.result().name(), "seatPrice", h.seatPrice());
    }

    @PostMapping("/internal/release")
    public Map<String, Object> release(@RequestBody Map<String, String> body) {
        service.release(body.get("flightId"), body.get("seat"), body.get("bookingId"));
        return Map.of("ok", true);
    }

    @PostMapping("/internal/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, String> body) {
        boolean ok = service.confirm(body.get("flightId"), body.get("seat"), body.get("bookingId"));
        return Map.of("confirmed", ok);
    }

    private static Map<String, Object> view(Flight f, List<Seat> seats) {
        Instant now = Instant.now();
        List<Map<String, Object>> seatViews = seats.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("seat", s.getLabel());
            boolean available = s.isEffectivelyAvailable(now);
            m.put("available", available);
            if (Seat.BOOKED.equals(s.getStatus()) && s.getOwnerBookingId() != null) {
                m.put("bookingId", s.getOwnerBookingId());
            }
            return m;
        }).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", f.getId());
        out.put("seatCount", f.getSeatCount());
        out.put("seatPrice", f.getSeatPrice());
        out.put("seats", seatViews);
        return out;
    }

    private static Integer intOrNull(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        if (v instanceof Number n) return n.intValue();
        return null;
    }
}
