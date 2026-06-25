package com.study.app.query.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Records which lists exist, so notification queries can 404 unknown lists. */
@Entity
@Table(name = "known_list")
public class KnownListEntity {
    @Id
    private String listId;

    public KnownListEntity() {}
    public KnownListEntity(String listId) { this.listId = listId; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
}
