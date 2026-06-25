package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.view.NotificationView;

public class GetNotificationsQuery extends Query<Multiple<NotificationView>> {
    private String listId;

    public GetNotificationsQuery() {}
    public GetNotificationsQuery(String listId) { this.listId = listId; }

    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
}
