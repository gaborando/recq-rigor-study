package com.study.app.query;

import com.study.app.domain.events.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ProcessingGroup("order-projection")
public class OrderProjection {

    private final OrderViewRepository orders;
    private final NotificationRepository notifications;

    public OrderProjection(OrderViewRepository orders, NotificationRepository notifications) {
        this.orders = orders;
        this.notifications = notifications;
    }

    @EventHandler
    public void on(OrderCreatedEvent e) {
        orders.save(new OrderView(e.orderId(), e.customerId(), e.productId(), e.quantity(), "PENDING"));
    }

    @EventHandler
    public void on(OrderConfirmedEvent e) {
        orders.findById(e.orderId()).ifPresent(v -> {
            v.setStatus("CONFIRMED");
            v.setTotal(e.total());
            orders.save(v);
        });
        orders.findById(e.orderId()).ifPresent(v ->
                emitNotification(e.orderId(), v.getCustomerId(), "CONFIRMED", null));
    }

    @EventHandler
    public void on(OrderRejectedEvent e) {
        orders.findById(e.orderId()).ifPresent(v -> {
            v.setStatus("REJECTED");
            v.setReason(e.reason());
            orders.save(v);
        });
        orders.findById(e.orderId()).ifPresent(v ->
                emitNotification(e.orderId(), v.getCustomerId(), "REJECTED", e.reason()));
    }

    private void emitNotification(String orderId, String customerId, String status, String reason) {
        try {
            notifications.saveAndFlush(new NotificationView(orderId, customerId, status, reason));
        } catch (DataIntegrityViolationException ignored) {
            // exactly-once guard: UNIQUE(orderId) constraint
        }
    }

    @QueryHandler
    public OrderView handle(FindOrder q) {
        return orders.findById(q.orderId()).orElse(null);
    }

    @QueryHandler
    public List<NotificationView> handle(FindCustomerNotifications q) {
        return notifications.findByCustomerId(q.customerId());
    }

    @QueryHandler
    public OrderStats handle(FindOrderStats q) {
        return new OrderStats(orders.countConfirmed(), orders.countRejected(), orders.sumRevenue());
    }
}
