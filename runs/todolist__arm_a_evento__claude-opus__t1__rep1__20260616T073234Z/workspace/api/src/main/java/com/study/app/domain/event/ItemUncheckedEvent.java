package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ItemUncheckedEvent extends DomainEvent {
    private String listId;
    private String itemId;

    public ItemUncheckedEvent() {}
    public ItemUncheckedEvent(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
}
