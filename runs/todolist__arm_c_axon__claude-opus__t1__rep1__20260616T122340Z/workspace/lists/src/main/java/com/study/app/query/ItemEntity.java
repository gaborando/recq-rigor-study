package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_view", indexes = @Index(name = "idx_item_list", columnList = "listId"))
public class ItemEntity {

    @Id
    private String itemId;
    private String listId;
    private String content;
    private boolean checked;

    protected ItemEntity() {
    }

    public ItemEntity(String itemId, String listId, String content, boolean checked) {
        this.itemId = itemId;
        this.listId = listId;
        this.content = content;
        this.checked = checked;
    }

    public String getItemId() {
        return itemId;
    }

    public String getListId() {
        return listId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }
}
