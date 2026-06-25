package com.study.app.web;

import com.study.app.command.ItemRepository;
import com.study.app.command.ItemService;
import com.study.app.query.ItemStatsView;
import com.study.app.query.ItemView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
public class ItemController {

    private final ItemService service;
    private final ItemRepository items;

    public ItemController(ItemService service, ItemRepository items) {
        this.service = service;
        this.items = items;
    }

    @PostMapping("/internal/lists/{id}/register")
    public void register(@PathVariable String id) {
        service.register(UUID.fromString(id));
    }

    public record AddItem(String itemId, String content) {}

    @PostMapping("/lists/{id}/items")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void add(@PathVariable String id, @RequestBody(required = false) AddItem body) {
        UUID listId = parseOr404(id);
        if (body == null || body.itemId() == null || body.itemId().isBlank()
                || body.content() == null || body.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId and content are required");
        }
        UUID itemId;
        try {
            itemId = UUID.fromString(body.itemId().trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "itemId must be a UUID");
        }
        service.add(listId, itemId, body.content());
    }

    @PutMapping("/lists/{id}/items/{itemId}/check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void check(@PathVariable String id, @PathVariable String itemId) {
        service.setChecked(parseOr404(id), parseItemOr404(itemId), true);
    }

    @PutMapping("/lists/{id}/items/{itemId}/uncheck")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void uncheck(@PathVariable String id, @PathVariable String itemId) {
        service.setChecked(parseOr404(id), parseItemOr404(itemId), false);
    }

    public record Rename(String content) {}

    @PutMapping("/lists/{id}/items/{itemId}/rename")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void rename(@PathVariable String id, @PathVariable String itemId,
                       @RequestBody(required = false) Rename body) {
        UUID listId = parseOr404(id);
        UUID iid = parseItemOr404(itemId);
        if (body == null || body.content() == null || body.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content is required");
        }
        service.rename(listId, iid, body.content());
    }

    @GetMapping("/lists/{id}/items")
    public List<ItemView> itemsOf(@PathVariable String id) {
        UUID listId;
        try {
            listId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        return items.findByKeyListId(listId).stream()
                .map(i -> new ItemView(i.getKey().getItemId(), i.getContent(), i.isChecked()))
                .toList();
    }

    @GetMapping("/stats")
    public ItemStatsView stats() {
        return new ItemStatsView(items.totalItems(), items.checkedItems());
    }

    private UUID parseOr404(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown list");
        }
    }

    private UUID parseItemOr404(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown item");
        }
    }
}
