package com.study.app.command;

import com.study.app.domain.CompletionOutbox;
import com.study.app.domain.Item;
import com.study.app.domain.ItemKey;
import com.study.app.domain.ListRef;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class ItemService {

    private final ItemRepository items;
    private final ListRefRepository listRefs;
    private final CompletionOutboxRepository outbox;
    private final RestClient listsClient;

    public ItemService(ItemRepository items, ListRefRepository listRefs,
                       CompletionOutboxRepository outbox, RestClient listsClient) {
        this.items = items;
        this.listRefs = listRefs;
        this.outbox = outbox;
        this.listsClient = listsClient;
    }

    /** Register a list known to this service (idempotent). */
    @Transactional
    public void register(UUID listId) {
        if (listRefs.existsById(listId)) return;
        try {
            listRefs.saveAndFlush(new ListRef(listId));
        } catch (DataIntegrityViolationException dup) {
            // concurrent register — fine
        }
    }

    @Transactional
    public void add(UUID listId, UUID itemId, String content) {
        ListRef ref = lockListOr404(listId);
        ItemKey key = new ItemKey(listId, itemId);
        if (!items.existsById(key)) {
            try {
                items.saveAndFlush(new Item(key, content));
            } catch (DataIntegrityViolationException dup) {
                // concurrent identical add — idempotent, content unchanged
            }
        }
        recomputeAndReport(ref);
    }

    @Transactional
    public void setChecked(UUID listId, UUID itemId, boolean checked) {
        ListRef ref = lockListOr404(listId);
        ItemKey key = new ItemKey(listId, itemId);
        if (!items.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown item");
        }
        items.setChecked(listId, itemId, checked);
        recomputeAndReport(ref);
    }

    @Transactional
    public void rename(UUID listId, UUID itemId, String content) {
        ListRef ref = lockListOr404(listId); // lock keeps rename from racing a check
        ItemKey key = new ItemKey(listId, itemId);
        if (!items.existsById(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown item");
        }
        items.setContent(listId, itemId, content);
        // rename never changes completeness, so no report
    }

    private ListRef lockListOr404(UUID listId) {
        return listRefs.findByIdForUpdate(listId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown list"));
    }

    /**
     * Recompute whether the list is fully checked and, if that changed since the
     * last report, enqueue a completeness report for the lists service. Runs
     * under the per-list lock, so the seq order equals the causal order of
     * completeness states.
     */
    private void recomputeAndReport(ListRef ref) {
        UUID listId = ref.getListId();
        long total = items.countByKeyListId(listId);
        long checked = items.countByKeyListIdAndCheckedTrue(listId);
        boolean complete = total >= 1 && checked == total;
        if (complete != ref.isLastReportedComplete()) {
            long seq = ref.getReportSeq() + 1;
            ref.setReportSeq(seq);
            ref.setLastReportedComplete(complete);
            listRefs.save(ref);
            outbox.save(new CompletionOutbox(listId, seq, complete));
        }
    }

    // ---- completeness outbox dispatch (at-least-once; idempotent at lists) ----

    @Transactional
    public void dispatchCompletion() {
        for (CompletionOutbox row : outbox.findTop200BySentFalseOrderByIdAsc()) {
            try {
                listsClient.post().uri("/internal/completion")
                        .body(Map.of("listId", row.getListId().toString(),
                                     "seq", row.getSeq(),
                                     "complete", row.isComplete()))
                        .retrieve().toBodilessEntity();
                row.setSent(true);
                outbox.save(row);
            } catch (RuntimeException e) {
                // leave unsent; retried next sweep
            }
        }
    }
}
