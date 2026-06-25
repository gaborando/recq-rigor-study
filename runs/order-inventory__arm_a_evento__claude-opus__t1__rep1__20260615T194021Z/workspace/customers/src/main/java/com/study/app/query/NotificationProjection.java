package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.query.NotificationsByCustomerQuery;
import com.study.app.domain.view.NotificationView;

import java.util.List;

@Projection
public class NotificationProjection {

    private final NotificationRepository repository;

    public NotificationProjection(NotificationRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Multiple<NotificationView> query(NotificationsByCustomerQuery q) {
        List<NotificationView> views = repository.findByCustomerId(q.getCustomerId()).stream()
                .map(e -> new NotificationView(e.getOrderId(), e.getStatus(), e.getReason()))
                .toList();
        return Multiple.of(views);
    }
}
