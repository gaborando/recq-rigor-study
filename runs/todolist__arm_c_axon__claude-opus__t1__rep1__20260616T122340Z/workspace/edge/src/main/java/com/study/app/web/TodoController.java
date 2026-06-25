package com.study.app.web;

import com.study.app.command.Commands.AddItemCommand;
import com.study.app.command.Commands.CheckItemCommand;
import com.study.app.command.Commands.CreateListCommand;
import com.study.app.command.Commands.RenameItemCommand;
import com.study.app.command.Commands.UncheckItemCommand;
import com.study.app.query.Queries.GetListViewQuery;
import com.study.app.query.Queries.GetNotificationsQuery;
import com.study.app.query.Queries.GetStatsQuery;
import com.study.app.query.Queries.ListSummaryResponse;
import com.study.app.query.Queries.ListViewResponse;
import com.study.app.query.Queries.NotificationResponse;
import com.study.app.query.Queries.NotificationsResponse;
import com.study.app.query.Queries.StatsResponse;
import java.util.List;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stateless HTTP front door. Translates the REST contract into Axon commands and
 * queries dispatched across Axon Server; holds no domain state or database.
 */
@RestController
public class TodoController {

    private final CommandGateway commandGateway;
    private final QueryGateway queryGateway;

    public TodoController(CommandGateway commandGateway, QueryGateway queryGateway) {
        this.commandGateway = commandGateway;
        this.queryGateway = queryGateway;
    }

    // ----- request bodies -----
    public record CreateListRequest(String listId, String name) {}

    public record AddItemRequest(String itemId, String content) {}

    public record RenameRequest(String content) {}

    // ----- lists -----

    @PostMapping("/lists")
    public ResponseEntity<ListSummaryResponse> createList(@RequestBody(required = false) CreateListRequest body) {
        if (body == null || isBlank(body.listId()) || isBlank(body.name())) {
            throw badRequest();
        }
        send(new CreateListCommand(body.listId(), body.name()));
        return ResponseEntity.accepted()
                .body(new ListSummaryResponse(body.listId(), body.name(), "ACTIVE"));
    }

    @GetMapping("/lists/{id}")
    public ListViewResponse getList(@PathVariable String id) {
        ListViewResponse view = queryGateway
                .query(new GetListViewQuery(id), ResponseTypes.instanceOf(ListViewResponse.class))
                .join();
        if (view == null) {
            throw notFound();
        }
        return view;
    }

    // ----- items -----

    @PostMapping("/lists/{id}/items")
    public ResponseEntity<Void> addItem(@PathVariable String id, @RequestBody(required = false) AddItemRequest body) {
        if (body == null || isBlank(body.itemId()) || isBlank(body.content())) {
            throw badRequest();
        }
        send(new AddItemCommand(body.itemId(), id, body.content()));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/lists/{id}/items/{itemId}/check")
    public ResponseEntity<Void> check(@PathVariable String id, @PathVariable String itemId) {
        send(new CheckItemCommand(itemId));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/lists/{id}/items/{itemId}/uncheck")
    public ResponseEntity<Void> uncheck(@PathVariable String id, @PathVariable String itemId) {
        send(new UncheckItemCommand(itemId));
        return ResponseEntity.accepted().build();
    }

    @PutMapping("/lists/{id}/items/{itemId}/rename")
    public ResponseEntity<Void> rename(@PathVariable String id, @PathVariable String itemId,
                                       @RequestBody(required = false) RenameRequest body) {
        if (body == null || isBlank(body.content())) {
            throw badRequest();
        }
        send(new RenameItemCommand(itemId, body.content()));
        return ResponseEntity.accepted().build();
    }

    // ----- notifications & stats -----

    @GetMapping("/lists/{id}/notifications")
    public List<NotificationResponse> notifications(@PathVariable String id) {
        NotificationsResponse response = queryGateway
                .query(new GetNotificationsQuery(id), ResponseTypes.instanceOf(NotificationsResponse.class))
                .join();
        if (response == null || !response.found()) {
            throw notFound();
        }
        return response.notifications();
    }

    @GetMapping("/stats/lists")
    public StatsResponse stats() {
        return queryGateway
                .query(new GetStatsQuery(), ResponseTypes.instanceOf(StatsResponse.class))
                .join();
    }

    // ----- helpers -----

    /**
     * Dispatch a command and block for its result. A failure relayed from the
     * owning aggregate that carries the item-not-found marker becomes HTTP 404;
     * any other failure propagates as a server error.
     */
    private void send(Object command) {
        try {
            commandGateway.sendAndWait(command);
        } catch (Exception ex) {
            if (isItemNotFound(ex)) {
                throw notFound();
            }
            throw ex;
        }
    }

    private static boolean isItemNotFound(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && (msg.contains("ITEM_NOT_FOUND")
                    || msg.contains("aggregate") && msg.contains("not found")
                    || msg.contains("was not found"))) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseStatusException badRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
