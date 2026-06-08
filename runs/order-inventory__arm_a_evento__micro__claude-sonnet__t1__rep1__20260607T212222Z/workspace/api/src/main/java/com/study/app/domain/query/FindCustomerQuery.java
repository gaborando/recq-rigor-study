package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.CustomerView;

public class FindCustomerQuery extends Query<Single<CustomerView>> {
    private String customerId;

    public FindCustomerQuery() {}

    public FindCustomerQuery(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
