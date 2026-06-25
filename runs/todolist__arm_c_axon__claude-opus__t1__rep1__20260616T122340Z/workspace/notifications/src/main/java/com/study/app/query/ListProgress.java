package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Per-list completion state, used to detect ACTIVE->COMPLETED transitions
 * exactly once. Persisted in the notifications database alongside the tracking
 * token, so a transition already recorded is never re-emitted after a restart.
 */
@Entity
@Table(name = "list_progress")
public class ListProgress {

    @Id
    private String listId;
    private boolean completed;
    private int completionCount;

    protected ListProgress() {
    }

    public ListProgress(String listId, boolean completed, int completionCount) {
        this.listId = listId;
        this.completed = completed;
        this.completionCount = completionCount;
    }

    public String getListId() {
        return listId;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public int getCompletionCount() {
        return completionCount;
    }

    public void setCompletionCount(int completionCount) {
        this.completionCount = completionCount;
    }
}
