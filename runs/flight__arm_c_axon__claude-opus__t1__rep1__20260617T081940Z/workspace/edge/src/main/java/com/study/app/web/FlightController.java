package com.study.app.web;

import com.study.app.api.command.FlightCommands.CreateFlight;
import com.study.app.api.query.Queries.FindFlight;
import com.study.app.api.query.Views.FlightView;
import com.study.app.api.query.Views.SeatView;
import com.study.app.api.support.Seats;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class FlightController {

    private final CommandGateway commandGateway;
    private final Gateways gateways;

    public FlightController(CommandGateway commandGateway, Gateways gateways) {
        this.commandGateway = commandGateway;
        this.gateways = gateways;
    }

    public record CreateFlightRequest(Integer seatCount, Integer seatPrice) {
    }

    @PostMapping("/flights")
    public ResponseEntity<FlightView> create(@RequestBody CreateFlightRequest body) {
        Gateways.require(body.seatCount() != null && body.seatCount() >= 1, "seatCount must be >= 1");
        Gateways.require(body.seatPrice() != null && body.seatPrice() >= 1, "seatPrice must be >= 1");

        String id = UUID.randomUUID().toString();
        // synchronous create: the aggregate exists before the client books against it
        commandGateway.sendAndWait(new CreateFlight(id, body.seatCount(), body.seatPrice()), 20, TimeUnit.SECONDS);

        var seats = Seats.labels(body.seatCount()).stream()
                .map(s -> new SeatView(s, true, null))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new FlightView(id, body.seatCount(), body.seatPrice(), seats));
    }

    @GetMapping("/flights/{id}")
    public FlightView get(@PathVariable String id) {
        return gateways.queryOr404(new FindFlight(id), FlightView.class);
    }
}
