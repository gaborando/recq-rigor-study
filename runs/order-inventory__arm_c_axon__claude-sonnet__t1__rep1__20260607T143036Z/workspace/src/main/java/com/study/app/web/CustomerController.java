package com.study.app.web;

import com.study.app.command.CreateCustomerCommand;
import com.study.app.command.DepositFundsCommand;
import com.study.app.query.*;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@RestController
public class CustomerController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public CustomerController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public record CreateCustomerRequest(String name, Integer balance) {}
    public record DepositRequest(Integer amount) {}

    @PostMapping("/customers")
    public ResponseEntity<?> create(@RequestBody CreateCustomerRequest body) {
        if (body.name() == null || body.name().isBlank()
                || body.balance() == null || body.balance() < 0) {
            return ResponseEntity.badRequest().body("Invalid customer fields");
        }
        String customerId = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new CreateCustomerCommand(customerId, body.name(), body.balance()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", customerId, "name", body.name(), "balance", body.balance()));
    }

    @GetMapping("/customers/{id}")
    public CustomerView get(@PathVariable String id) throws ExecutionException, InterruptedException {
        CustomerView v = queryGateway.query(new FindCustomer(id), ResponseTypes.instanceOf(CustomerView.class)).get();
        if (v == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return v;
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<?> deposit(@PathVariable String id, @RequestBody DepositRequest body) {
        if (body.amount() == null || body.amount() < 1) {
            return ResponseEntity.badRequest().body("amount must be >= 1");
        }
        try {
            commandGateway.send(new DepositFundsCommand(id, body.amount()));
        } catch (Exception e) {
            if (isNotFound(e)) return ResponseEntity.notFound().build();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/customers/{id}/notifications")
    public ResponseEntity<List<NotificationView>> notifications(@PathVariable String id)
            throws ExecutionException, InterruptedException {
        CustomerView customer = queryGateway.query(new FindCustomer(id),
                ResponseTypes.instanceOf(CustomerView.class)).get();
        if (customer == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        List<NotificationView> list = queryGateway.query(
                new FindCustomerNotifications(id),
                ResponseTypes.multipleInstancesOf(NotificationView.class)).get();
        return ResponseEntity.ok(list);
    }

    private boolean isNotFound(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof org.axonframework.modelling.command.AggregateNotFoundException) return true;
            cause = cause.getCause();
        }
        return false;
    }
}
