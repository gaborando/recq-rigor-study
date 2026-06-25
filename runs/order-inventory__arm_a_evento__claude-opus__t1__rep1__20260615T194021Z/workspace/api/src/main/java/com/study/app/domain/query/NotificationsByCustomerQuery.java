package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.view.NotificationView;

public class NotificationsByCustomerQuery extends Query<Multiple<NotificationView>> {
    private String customerId;

    public NotificationsByCustomerQuery() {}
    public NotificationsByCustomerQuery(String customerId) { this.customerId = customerId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
