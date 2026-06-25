package com.study.app.web;

import com.study.app.command.ListService;
import com.study.app.command.TodoListRepository;
import com.study.app.domain.TodoList;
import com.study.app.query.ListSummary;
import com.study.app.query.StatsView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
public class ListController {

    private final ListService service;
    private final TodoListRepository lists;

    public ListController(ListService service, TodoListRepository lists) {
        this.service = service;
        this.lists = lists;
    }

    public record CreateList(String listId, String name) {}

    @PostMapping("/lists")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ListSummary create(@RequestBody(required = false) CreateList body) {
        if (body == null || body.listId() == null || body.listId().isBlank()
                || body.name() == null || body.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "listId and name are required");
        }
        UUID id;
        try {
            id = UUID.fromString(body.listId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "listId must be a UUID");
        }
        return service.create(id, body.name());
    }

    @GetMapping("/lists/{id}")
    public ListSummary get(@PathVariable String id) {
        UUID listId = parseOr404(id);
        TodoList l = lists.findById(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown list"));
        return new ListSummary(l.getId(), l.getName(), l.getStatus());
    }

    @GetMapping("/stats")
    public StatsView stats() {
        return new StatsView(lists.countActive(), lists.countCompleted());
    }

    public record CompletionReport(String listId, Long seq, Boolean complete) {}

    @PostMapping("/internal/completion")
    public ResponseEntity<Void> applyCompletion(@RequestBody CompletionReport body) {
        service.applyCompletion(UUID.fromString(body.listId()), body.seq(), body.complete());
        return ResponseEntity.ok().build();
    }

    private UUID parseOr404(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown list");
        }
    }
}
