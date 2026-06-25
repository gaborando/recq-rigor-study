package com.study.app.web;

import com.evento.application.EventoBundle;
import com.study.app.domain.view.ListView;
import com.study.app.domain.view.NotificationView;
import com.study.app.domain.view.StatsView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;

@RestController
public class TodoController {

    private final TodoInvoker invoker;

    public TodoController(EventoBundle eventoBundle) {
        this.invoker = eventoBundle.getInvoker(TodoInvoker.class);
    }

    public record CreateListRequest(String listId, String name) {}
    public record AddItemRequest(String itemId, String content) {}
    public record ContentRequest(String content) {}

    // ---- lists -------------------------------------------------------------

    @PostMapping("/lists")
    public ResponseEntity<Map<String, String>> createList(@RequestBody(required = false) CreateListRequest body) {
        if (body == null || isBlank(body.listId()) || isBlank(body.name())) {
            throw badRequest("listId and non-empty name are required");
        }
        call(() -> invoker.createList(body.listId(), body.name()));
        return ResponseEntity.accepted()
                .body(Map.of("listId", body.listId(), "name", body.name(), "status", "ACTIVE"));
    }

    @GetMapping("/lists/{id}")
    public ListView getList(@PathVariable String id) {
        return query(() -> invoker.getList(id));
    }

    // ---- items -------------------------------------------------------------

    @PostMapping("/lists/{id}/items")
    public ResponseEntity<Void> addItem(@PathVariable String id, @RequestBody(required = false) AddItemRequest body) {
        if (body == null || isBlank(body.itemId()) || isBlank(body.content())) {
            throw badRequest("itemId and non-empty content are required");
        }
        call(() -> invoker.addItem(id, body.itemId(), body.content()));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/lists/{id}/items/{itemId}/check")
    public ResponseEntity<Void> check(@PathVariable String id, @PathVariable String itemId) {
        call(() -> invoker.checkItem(id, itemId));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/lists/{id}/items/{itemId}/uncheck")
    public ResponseEntity<Void> uncheck(@PathVariable String id, @PathVariable String itemId) {
        call(() -> invoker.uncheckItem(id, itemId));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/lists/{id}/items/{itemId}/rename")
    public ResponseEntity<Void> rename(@PathVariable String id, @PathVariable String itemId,
                                       @RequestBody(required = false) ContentRequest body) {
        if (body == null || isBlank(body.content())) {
            throw badRequest("non-empty content is required");
        }
        call(() -> invoker.renameItem(id, itemId, body.content()));
        return ResponseEntity.accepted().build();
    }

    // ---- notifications & stats --------------------------------------------

    @GetMapping("/lists/{id}/notifications")
    public Collection<NotificationView> notifications(@PathVariable String id) {
        return query(() -> invoker.getNotifications(id));
    }

    @GetMapping("/stats/lists")
    public StatsView stats() {
        return query(invoker::getStats);
    }

    // ---- plumbing ----------------------------------------------------------

    @FunctionalInterface
    private interface Action { void run() throws Exception; }

    @FunctionalInterface
    private interface Supplier<T> { T get() throws Exception; }

    private static void call(Action action) {
        try {
            action.run();
        } catch (Exception e) {
            throw translate(e);
        }
    }

    private static <T> T query(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw translate(e);
        }
    }

    private static RuntimeException translate(Exception e) {
        if (e instanceof ResponseStatusException rse) return rse;
        if (EventoErrors.isNotFound(e)) {
            return new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
