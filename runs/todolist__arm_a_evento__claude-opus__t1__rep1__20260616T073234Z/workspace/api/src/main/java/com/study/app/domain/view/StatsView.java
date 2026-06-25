package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class StatsView implements View {
    private long active;
    private long completed;
    private long totalItems;
    private long checkedItems;

    public StatsView() {}
    public StatsView(long active, long completed, long totalItems, long checkedItems) {
        this.active = active;
        this.completed = completed;
        this.totalItems = totalItems;
        this.checkedItems = checkedItems;
    }

    public long getActive() { return active; }
    public void setActive(long active) { this.active = active; }
    public long getCompleted() { return completed; }
    public void setCompleted(long completed) { this.completed = completed; }
    public long getTotalItems() { return totalItems; }
    public void setTotalItems(long totalItems) { this.totalItems = totalItems; }
    public long getCheckedItems() { return checkedItems; }
    public void setCheckedItems(long checkedItems) { this.checkedItems = checkedItems; }
}
