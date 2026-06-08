package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.FindCustomerNotificationsQuery;
import com.study.app.domain.query.FindCustomerQuery;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;

import java.util.List;
import java.util.NoSuchElementException;

@Projection
public class CustomerProjection {

    private final CustomerRepository customerRepository;
    private final NotificationRepository notificationRepository;

    public CustomerProjection(CustomerRepository customerRepository,
                              NotificationRepository notificationRepository) {
        this.customerRepository = customerRepository;
        this.notificationRepository = notificationRepository;
    }

    @QueryHandler
    Single<CustomerView> query(FindCustomerQuery q) {
        CustomerEntity entity = customerRepository.findById(q.getCustomerId())
                .orElseThrow(() -> new NoSuchElementException("customer not found: " + q.getCustomerId()));
        return Single.of(new CustomerView(entity.getId(), entity.getName(), entity.getBalance()));
    }

    @QueryHandler
    Multiple<NotificationView> query(FindCustomerNotificationsQuery q) {
        customerRepository.findById(q.getCustomerId())
                .orElseThrow(() -> new NoSuchElementException("customer not found: " + q.getCustomerId()));
        List<NotificationView> views = notificationRepository.findByCustomerId(q.getCustomerId())
                .stream()
                .map(n -> new NotificationView(n.getOrderId(), n.getStatus(), n.getReason()))
                .toList();
        return Multiple.of(views);
    }
}
