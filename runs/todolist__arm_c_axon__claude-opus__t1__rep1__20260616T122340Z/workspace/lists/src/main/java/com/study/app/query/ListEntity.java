package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "list_view")
public class ListEntity {

    @Id
    private String listId;
    private String name;
    private String status; // ACTIVE | COMPLETED

    protected ListEntity() {
    }

    public ListEntity(String listId, String name, String status) {
        this.listId = listId;
        this.name = name;
        this.status = status;
    }

    public String getListId() {
        return listId;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
