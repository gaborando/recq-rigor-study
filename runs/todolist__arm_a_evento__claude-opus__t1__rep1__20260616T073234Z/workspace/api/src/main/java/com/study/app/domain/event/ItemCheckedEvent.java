package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

/**
 * Emitted for every check command (including idempotent no-ops). The aggregate
 * decides atomically whether THIS check transitioned the list ACTIVE -> COMPLETED;
 * when it did, {@code completedTransition} is true and {@code completionSeq} is the
 * monotonically increasing ordinal of that transition for the list (used as a stable
 * idempotency key so the notification is recorded exactly once).
 */
public class ItemCheckedEvent extends DomainEvent {
    private String listId;
    private String itemId;
    private boolean completedTransition;
    private long completionSeq;

    public ItemCheckedEvent() {}
    public ItemCheckedEvent(String listId, String itemId, boolean completedTransition, long completionSeq) {
        this.listId = listId;
        this.itemId = itemId;
        this.completedTransition = completedTransition;
        this.completionSeq = completionSeq;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public boolean isCompletedTransition() { return completedTransition; }
    public void setCompletedTransition(boolean completedTransition) { this.completedTransition = completedTransition; }
    public long getCompletionSeq() { return completionSeq; }
    public void setCompletionSeq(long completionSeq) { this.completionSeq = completionSeq; }
}
