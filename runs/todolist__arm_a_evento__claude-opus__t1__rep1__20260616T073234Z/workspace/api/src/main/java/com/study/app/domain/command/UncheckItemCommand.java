package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class UncheckItemCommand extends DomainCommand {
    private String listId;
    private String itemId;

    public UncheckItemCommand() {}
    public UncheckItemCommand(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    @Override
    public String getAggregateId() { return listId; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
}
