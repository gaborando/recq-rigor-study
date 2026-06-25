package com.study.app.query.store;

import java.io.Serializable;
import java.util.Objects;

public class ItemKey implements Serializable {
    private String listId;
    private String itemId;

    public ItemKey() {}
    public ItemKey(String listId, String itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey)) return false;
        ItemKey k = (ItemKey) o;
        return Objects.equals(listId, k.listId) && Objects.equals(itemId, k.itemId);
    }

    @Override
    public int hashCode() { return Objects.hash(listId, itemId); }
}
