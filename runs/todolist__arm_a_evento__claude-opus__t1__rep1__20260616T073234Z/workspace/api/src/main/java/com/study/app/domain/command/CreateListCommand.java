package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class CreateListCommand extends DomainCommand {
    private String listId;
    private String name;

    public CreateListCommand() {}
    public CreateListCommand(String listId, String name) {
        this.listId = listId;
        this.name = name;
    }

    @Override
    public String getAggregateId() { return listId; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
