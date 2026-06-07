package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.view.NotificationView;

public class GetCustomerNotificationsQuery extends Query<Multiple<NotificationView>> {
    private String customerId;

    public GetCustomerNotificationsQuery() {}

    public GetCustomerNotificationsQuery(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
