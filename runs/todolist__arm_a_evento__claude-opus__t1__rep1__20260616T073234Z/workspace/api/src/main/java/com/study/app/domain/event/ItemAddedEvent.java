package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ItemAddedEvent extends DomainEvent {
    private String listId;
    private String itemId;
    private String content;

    public ItemAddedEvent() {}
    public ItemAddedEvent(String listId, String itemId, String content) {
        this.listId = listId;
        this.itemId = itemId;
        this.content = content;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
