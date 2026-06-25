package com.study.app.web;

import com.study.app.api.command.BookingCommands.CreateBooking;
import com.study.app.api.query.Queries.FindBooking;
import com.study.app.api.query.Views.BookingView;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class BookingController {

    private final CommandGateway commandGateway;
    private final Gateways gateways;

    public BookingController(CommandGateway commandGateway, Gateways gateways) {
        this.commandGateway = commandGateway;
        this.gateways = gateways;
    }

    public record CreateBookingRequest(String bookingId, String customerId, String flightId, String seat) {
    }

    @PostMapping("/bookings")
    public ResponseEntity<Map<String, Object>> create(@RequestBody CreateBookingRequest body) {
        Gateways.require(body.bookingId() != null && !body.bookingId().isBlank(), "bookingId required");
        Gateways.require(body.customerId() != null && !body.customerId().isBlank(), "customerId required");
        Gateways.require(body.flightId() != null && !body.flightId().isBlank(), "flightId required");
        Gateways.require(body.seat() != null && !body.seat().isBlank(), "seat required");

        // Fire-and-forget: creation is idempotent on bookingId (aggregate identity +
        // optimistic concurrency), so a retry storm of the same id is harmless.
        commandGateway.send(new CreateBooking(body.bookingId(), body.customerId(), body.flightId(), body.seat()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("bookingId", body.bookingId(), "status", "PENDING"));
    }

    @GetMapping("/bookings/{id}")
    public BookingView get(@PathVariable String id) {
        return gateways.queryOr404(new FindBooking(id), BookingView.class);
    }
}
