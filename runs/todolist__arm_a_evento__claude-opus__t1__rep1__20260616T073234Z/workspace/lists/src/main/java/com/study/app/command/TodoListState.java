package com.study.app.command;

import com.evento.common.modeling.state.AggregateState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event-sourced state of one todo list (one aggregate per listId). It tracks just
 * enough to make consistency decisions: which items exist, their checked flag, and
 * how many ACTIVE -> COMPLETED transitions have happened (the completion ordinal).
 * Item content is NOT held here — it lives in the read models.
 */
public class TodoListState extends AggregateState {
    private String name;
    private Map<String, Boolean> items = new LinkedHashMap<>();
    private long completions;

    public TodoListState() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Map<String, Boolean> getItems() { return items; }
    public void setItems(Map<String, Boolean> items) { this.items = items; }
    public long getCompletions() { return completions; }
    public void setCompletions(long completions) { this.completions = completions; }

    public boolean hasItem(String itemId) { return items.containsKey(itemId); }

    public boolean isComplete() {
        if (items.isEmpty()) return false;
        for (Boolean checked : items.values()) {
            if (!Boolean.TRUE.equals(checked)) return false;
        }
        return true;
    }

    public long uncheckedCount() {
        long n = 0;
        for (Boolean checked : items.values()) {
            if (!Boolean.TRUE.equals(checked)) n++;
        }
        return n;
    }
}
