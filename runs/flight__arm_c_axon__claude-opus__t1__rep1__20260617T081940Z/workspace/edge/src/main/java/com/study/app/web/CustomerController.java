package com.study.app.web;

import com.study.app.api.command.CustomerCommands.CreateCustomer;
import com.study.app.api.command.CustomerCommands.Deposit;
import com.study.app.api.query.Queries.FindCustomer;
import com.study.app.api.query.Queries.FindNotifications;
import com.study.app.api.query.Views.CustomerView;
import com.study.app.api.query.Views.NotificationList;
import com.study.app.api.query.Views.NotificationView;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
public class CustomerController {

    private final CommandGateway commandGateway;
    private final Gateways gateways;

    public CustomerController(CommandGateway commandGateway, Gateways gateways) {
        this.commandGateway = commandGateway;
        this.gateways = gateways;
    }

    public record CreateCustomerRequest(String name, Long balance) {
    }

    public record DepositRequest(Long amount) {
    }

    @PostMapping("/customers")
    public ResponseEntity<CustomerView> create(@RequestBody CreateCustomerRequest body) {
        Gateways.require(body.name() != null && !body.name().isBlank(), "name required");
        Gateways.require(body.balance() != null && body.balance() >= 0, "balance must be >= 0");

        String id = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new CreateCustomer(id, body.name(), body.balance()), 20, TimeUnit.SECONDS);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerView(id, body.name(), body.balance()));
    }

    @GetMapping("/customers/{id}")
    public CustomerView get(@PathVariable String id) {
        return gateways.queryOr404(new FindCustomer(id), CustomerView.class);
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<Map<String, Object>> deposit(@PathVariable String id, @RequestBody DepositRequest body) {
        Gateways.require(body.amount() != null && body.amount() >= 1, "amount must be >= 1");
        commandGateway.send(new Deposit(id, body.amount()));
        return ResponseEntity.accepted().body(Map.of("customerId", id, "amount", body.amount()));
    }

    @GetMapping("/customers/{id}/notifications")
    public List<NotificationView> notifications(@PathVariable String id) {
        NotificationList result = gateways.querySingle(new FindNotifications(id), NotificationList.class);
        return result != null ? result.notifications() : List.of();
    }
}
