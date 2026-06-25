package com.study.app.domain.event;

import com.evento.common.modeling.messaging.payload.DomainEvent;

public class ListCreatedEvent extends DomainEvent {
    private String listId;
    private String name;

    public ListCreatedEvent() {}
    public ListCreatedEvent(String listId, String name) {
        this.listId = listId;
        this.name = name;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
