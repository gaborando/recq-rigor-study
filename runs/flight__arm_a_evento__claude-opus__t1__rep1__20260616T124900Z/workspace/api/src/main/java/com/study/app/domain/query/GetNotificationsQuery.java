package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.view.NotificationView;

public class GetNotificationsQuery extends Query<Multiple<NotificationView>> {
    private String customerId;

    public GetNotificationsQuery() {}
    public GetNotificationsQuery(String customerId) { this.customerId = customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
