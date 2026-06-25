package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** Lists known to this service, so GET notifications can 404 an unknown list. */
@Entity
@Table(name = "list_ref")
public class ListRef {

    @Id
    private UUID listId;

    protected ListRef() {}

    public ListRef(UUID listId) {
        this.listId = listId;
    }

    public UUID getListId() { return listId; }
}
