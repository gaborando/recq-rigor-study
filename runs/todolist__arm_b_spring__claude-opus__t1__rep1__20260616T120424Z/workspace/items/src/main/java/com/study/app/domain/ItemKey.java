package com.study.app.domain;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Item identity is (listId, itemId): item ops are idempotent on this pair. */
@Embeddable
public class ItemKey implements Serializable {

    private UUID listId;
    private UUID itemId;

    protected ItemKey() {}

    public ItemKey(UUID listId, UUID itemId) {
        this.listId = listId;
        this.itemId = itemId;
    }

    public UUID getListId() { return listId; }
    public UUID getItemId() { return itemId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey k)) return false;
        return Objects.equals(listId, k.listId) && Objects.equals(itemId, k.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(listId, itemId);
    }
}
