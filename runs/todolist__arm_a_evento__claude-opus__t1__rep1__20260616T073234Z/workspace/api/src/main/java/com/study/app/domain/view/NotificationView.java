package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class NotificationView implements View {
    private String listId;
    private String status;

    public NotificationView() {}
    public NotificationView(String listId, String status) {
        this.listId = listId;
        this.status = status;
    }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
