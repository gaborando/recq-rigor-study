package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * The checked state of each item, replicated locally from the item event stream
 * so completion can be recomputed idempotently (state-based, not delta-based).
 */
@Entity
@Table(name = "notif_item_state",
        indexes = @Index(name = "idx_notif_item_list", columnList = "listId"))
public class NotifItemState {

    @Id
    private String itemId;
    private String listId;
    private boolean checked;

    protected NotifItemState() {
    }

    public NotifItemState(String itemId, String listId, boolean checked) {
        this.itemId = itemId;
        this.listId = listId;
        this.checked = checked;
    }

    public String getItemId() {
        return itemId;
    }

    public String getListId() {
        return listId;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }
}
