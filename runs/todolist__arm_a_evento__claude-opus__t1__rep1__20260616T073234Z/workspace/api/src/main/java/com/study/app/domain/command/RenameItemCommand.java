package com.study.app.domain.command;

import com.evento.common.modeling.messaging.payload.DomainCommand;

public class RenameItemCommand extends DomainCommand {
    private String listId;
    private String itemId;
    private String content;

    public RenameItemCommand() {}
    public RenameItemCommand(String listId, String itemId, String content) {
        this.listId = listId;
        this.itemId = itemId;
        this.content = content;
    }

    @Override
    public String getAggregateId() { return listId; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
