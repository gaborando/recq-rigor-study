package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

import java.util.ArrayList;
import java.util.List;

public class ListView implements View {
    private String listId;
    private String name;
    private String status;
    private List<ItemView> items = new ArrayList<>();

    public ListView() {}
    public ListView(String listId, String name, String status, List<ItemView> items) {
        this.listId = listId;
        this.name = name;
        this.status = status;
        this.items = items;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<ItemView> getItems() { return items; }
    public void setItems(List<ItemView> items) { this.items = items; }
}
