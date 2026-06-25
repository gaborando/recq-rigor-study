package com.study.app.command;

import com.study.app.domain.NotifOutbox;
import com.study.app.domain.TodoList;
import com.study.app.query.ListSummary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

@Service
public class ListService {

    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";

    private final TodoListRepository lists;
    private final NotifOutboxRepository outbox;
    private final RestClient itemsClient;
    private final RestClient notificationsClient;
    private final ListService self;

    public ListService(TodoListRepository lists,
                       NotifOutboxRepository outbox,
                       @Qualifier("itemsClient") RestClient itemsClient,
                       @Qualifier("notificationsClient") RestClient notificationsClient,
                       @Lazy ListService self) {
        this.lists = lists;
        this.outbox = outbox;
        this.itemsClient = itemsClient;
        this.notificationsClient = notificationsClient;
        this.self = self;
    }

    /**
     * Create a list, idempotent on listId. Before returning, synchronously
     * registers the list with the items and notifications services so that
     * immediate follow-up item operations (which the test issues without a
     * polling barrier) find the list rather than 404-ing.
     */
    public ListSummary create(UUID id, String name) {
        TodoList l = self.insertOrGet(id, name);
        register(id);
        return new ListSummary(id, l.getName(), l.getStatus());
    }

    @Transactional
    public TodoList insertOrGet(UUID id, String name) {
        var existing = lists.findById(id);
        if (existing.isPresent()) return existing.get();
        try {
            return lists.saveAndFlush(new TodoList(id, name, ACTIVE));
        } catch (DataIntegrityViolationException dup) { // concurrent first-create
            return lists.findById(id).orElseThrow();
        }
    }

    private void register(UUID id) {
        registerWith(itemsClient, id);
        registerWith(notificationsClient, id);
    }

    private void registerWith(RestClient client, UUID id) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                client.post().uri("/internal/lists/{id}/register", id.toString())
                        .retrieve().toBodilessEntity();
                return;
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
    }

    /**
     * Apply a completeness report from the items service. Serialized per list by
     * a row lock; stale/duplicate reports are rejected by the seq guard. On an
     * ACTIVE -> COMPLETED edge it bumps the completion counter and enqueues
     * exactly one notification outbox row.
     */
    @Transactional
    public void applyCompletion(UUID listId, long seq, boolean complete) {
        TodoList l = lists.findByIdForUpdate(listId).orElse(null);
        if (l == null) {
            // List not created here yet — signal retry to the caller.
            throw new IllegalStateException("unknown list " + listId);
        }
        if (seq <= l.getAppliedSeq()) return; // stale or duplicate report
        l.setAppliedSeq(seq);

        if (complete && ACTIVE.equals(l.getStatus())) {
            l.setStatus(COMPLETED);
            l.setCompletionSeq(l.getCompletionSeq() + 1);
            outbox.save(new NotifOutbox(listId, l.getCompletionSeq()));
        } else if (!complete && COMPLETED.equals(l.getStatus())) {
            l.setStatus(ACTIVE);
        }
        lists.save(l);
    }

    // ---- notification outbox dispatch (at-least-once; dedup at notifications) ----

    @Transactional
    public void dispatchNotifications() {
        for (NotifOutbox row : outbox.findTop200BySentFalseOrderByIdAsc()) {
            try {
                notificationsClient.post().uri("/internal/notifications")
                        .body(Map.of("listId", row.getListId().toString(),
                                     "transitionSeq", row.getTransitionSeq()))
                        .retrieve().toBodilessEntity();
                row.setSent(true);
                outbox.save(row);
            } catch (RuntimeException e) {
                // leave unsent; retried on the next sweep
            }
        }
    }
}
