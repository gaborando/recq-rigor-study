package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetCustomerQuery;
import com.study.app.domain.query.GetNotificationsQuery;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

@Projection
public class CustomerProjection {

    private final CustomerRepository customers;
    private final NotificationRepository notifications;

    public CustomerProjection(CustomerRepository customers, NotificationRepository notifications) {
        this.customers = customers;
        this.notifications = notifications;
    }

    @QueryHandler
    Single<CustomerView> query(GetCustomerQuery q) {
        CustomerEntity c = customers.findById(q.getCustomerId())
                .orElseThrow(() -> new NoSuchElementException("customer not found: " + q.getCustomerId()));
        return Single.of(new CustomerView(c.getId(), c.getName(), c.getBalance()));
    }

    @QueryHandler
    Multiple<NotificationView> query(GetNotificationsQuery q) {
        List<NotificationView> views = new ArrayList<>();
        for (NotificationEntity n : notifications.findByCustomerId(q.getCustomerId())) {
            views.add(new NotificationView(n.getBookingId(), n.getStatus(), n.getReason()));
        }
        return Multiple.of(views);
    }
}
