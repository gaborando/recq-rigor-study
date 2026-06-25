package com.study.app.web;

import com.study.app.command.CreateCustomerCommand;
import com.study.app.command.DepositCommand;
import com.study.app.query.CustomerDto;
import com.study.app.query.FindCustomer;
import com.study.app.query.FindNotifications;
import com.study.app.query.NotificationDto;
import com.study.app.query.NotificationList;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public CustomerController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    public record CreateCustomerRequest(String name, Integer balance) {}
    public record DepositRequest(Integer amount) {}

    @PostMapping
    public ResponseEntity<CustomerDto> create(@RequestBody CreateCustomerRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()
                || req.balance() == null || req.balance() < 0) {
            throw new IllegalArgumentException("invalid customer");
        }
        String id = UUID.randomUUID().toString();
        commandGateway.sendAndWait(new CreateCustomerCommand(id, req.name(), req.balance()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CustomerDto(id, req.name(), req.balance()));
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<Void> deposit(@PathVariable String id, @RequestBody DepositRequest req) {
        if (req == null || req.amount() == null || req.amount() < 1) {
            throw new IllegalArgumentException("invalid amount");
        }
        try {
            commandGateway.sendAndWait(new DepositCommand(id, req.amount()));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}")
    public CustomerDto get(@PathVariable String id) {
        CustomerDto dto = queryGateway.query(new FindCustomer(id), ResponseTypes.instanceOf(CustomerDto.class)).join();
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return dto;
    }

    @GetMapping("/{id}/notifications")
    public List<NotificationDto> notifications(@PathVariable String id) {
        NotificationList result = queryGateway.query(new FindNotifications(id),
                ResponseTypes.instanceOf(NotificationList.class)).join();
        return result == null ? List.of() : result.notifications();
    }
}
