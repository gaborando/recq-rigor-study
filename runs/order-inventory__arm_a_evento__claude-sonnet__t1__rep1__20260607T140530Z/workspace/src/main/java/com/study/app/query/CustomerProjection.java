package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetCustomerNotificationsQuery;
import com.study.app.domain.query.GetCustomerQuery;
import com.study.app.domain.view.CustomerView;
import com.study.app.domain.view.NotificationView;
import com.study.app.query.repository.CustomerRepository;
import com.study.app.query.repository.NotificationRepository;

import java.util.List;
import java.util.NoSuchElementException;

@Projection
public class CustomerProjection {

    private final CustomerRepository customerRepository;
    private final NotificationRepository notificationRepository;

    public CustomerProjection(CustomerRepository customerRepository, NotificationRepository notificationRepository) {
        this.customerRepository = customerRepository;
        this.notificationRepository = notificationRepository;
    }

    @QueryHandler
    Single<CustomerView> query(GetCustomerQuery q) {
        return customerRepository.findById(q.getCustomerId())
                .map(e -> Single.of(new CustomerView(e.getId(), e.getName(), e.getBalance())))
                .orElseThrow(() -> new NoSuchElementException("customer not found: " + q.getCustomerId()));
    }

    @QueryHandler
    Multiple<NotificationView> query(GetCustomerNotificationsQuery q) {
        if (!customerRepository.existsById(q.getCustomerId())) {
            throw new NoSuchElementException("customer not found: " + q.getCustomerId());
        }
        List<NotificationView> views = notificationRepository.findByCustomerId(q.getCustomerId())
                .stream()
                .map(e -> new NotificationView(e.getOrderId(), e.getStatus(), e.getReason()))
                .toList();
        return Multiple.of(views);
    }
}
