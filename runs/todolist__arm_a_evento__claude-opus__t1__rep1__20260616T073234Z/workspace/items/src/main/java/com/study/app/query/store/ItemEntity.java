package com.study.app.query.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "item_view")
@IdClass(ItemKey.class)
public class ItemEntity {
    @Id
    private String listId;
    @Id
    private String itemId;
    @Column(length = 100000)
    private String content;
    private boolean checked;

    public ItemEntity() {}
    public ItemEntity(String listId, String itemId, String content, boolean checked) {
        this.listId = listId;
        this.itemId = itemId;
        this.content = content;
        this.checked = checked;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }
}
