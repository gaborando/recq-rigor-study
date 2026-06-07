package com.study.app.web;

import com.evento.application.EventoBundle;
import com.evento.common.modeling.exceptions.AggregateNotInitializedError;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@RestController
public class CustomerController {

    private final CustomerInvoker invoker;

    public CustomerController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(CustomerInvoker.class);
    }

    record CreateCustomerRequest(String name, Integer balance) {}
    record DepositRequest(Integer amount) {}

    @PostMapping("/customers")
    public ResponseEntity<?> createCustomer(@RequestBody CreateCustomerRequest r) {
        if (r.name() == null || r.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "name is required"));
        }
        if (r.balance() == null || r.balance() < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "balance must be >= 0"));
        }
        try {
            String customerId = invoker.createCustomer(r.name(), r.balance());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", customerId, "name", r.name(), "balance", r.balance()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<?> getCustomer(@PathVariable String id) {
        try {
            CustomerView view = invoker.getCustomer(id);
            return ResponseEntity.ok(view);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/customers/{id}/deposit")
    public ResponseEntity<?> deposit(@PathVariable String id, @RequestBody DepositRequest r) {
        if (r.amount() == null || r.amount() < 1) {
            return ResponseEntity.badRequest().body(Map.of("error", "amount must be >= 1"));
        }
        try {
            invoker.depositFunds(id, r.amount());
            return ResponseEntity.accepted().build();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof AggregateNotInitializedError || cause instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            }
            String name = cause == null ? "" : cause.getClass().getSimpleName();
            if (name.contains("NotInitialized")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/customers/{id}/notifications")
    public ResponseEntity<?> getNotifications(@PathVariable String id) {
        try {
            Collection<NotificationView> notifications = invoker.getNotifications(id);
            return ResponseEntity.ok(notifications);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof NoSuchElementException) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
