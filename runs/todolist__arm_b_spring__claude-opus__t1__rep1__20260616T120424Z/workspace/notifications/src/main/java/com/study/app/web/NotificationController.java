package com.study.app.web;

import com.study.app.command.NotificationService;
import com.study.app.query.NotificationView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/internal/lists/{id}/register")
    public void register(@PathVariable String id) {
        service.register(UUID.fromString(id));
    }

    public record RecordNotification(String listId, Long transitionSeq) {}

    @PostMapping("/internal/notifications")
    public void record(@RequestBody RecordNotification body) {
        service.record(UUID.fromString(body.listId()), body.transitionSeq());
    }

    @GetMapping("/lists/{id}/notifications")
    public List<NotificationView> forList(@PathVariable String id) {
        UUID listId;
        try {
            listId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown list");
        }
        return service.forList(listId).stream()
                .map(n -> new NotificationView(n.getListId(), n.getStatus()))
                .toList();
    }
}
