package com.study.app.query.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "list_view")
public class ListEntity {
    @Id
    private String listId;
    private String name;

    public ListEntity() {}
    public ListEntity(String listId, String name) {
        this.listId = listId;
        this.name = name;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
