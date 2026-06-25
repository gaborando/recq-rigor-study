package com.study.app.query;

import com.study.app.domain.*;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

@Component
@ProcessingGroup("order-projection")
public class OrderProjection {

    private final OrderViewRepository orders;

    public OrderProjection(OrderViewRepository orders) {
        this.orders = orders;
    }

    @EventHandler
    public void on(OrderCreatedEvent e) {
        if (!orders.existsById(e.orderId())) {
            orders.save(new OrderView(e.orderId(), e.customerId(), e.productId(), e.quantity(), "PENDING"));
        }
    }

    @EventHandler
    public void on(OrderConfirmedEvent e) {
        orders.findById(e.orderId()).ifPresent(v -> {
            v.setStatus("CONFIRMED");
            v.setTotal(e.total());
            v.setReason(null);
            orders.save(v);
        });
    }

    @EventHandler
    public void on(OrderRejectedEvent e) {
        orders.findById(e.orderId()).ifPresent(v -> {
            v.setStatus("REJECTED");
            v.setReason(e.reason());
            orders.save(v);
        });
    }

    @QueryHandler
    public OrderDto handle(com.study.app.query.FindOrder q) {
        return orders.findById(q.orderId())
                .map(v -> new OrderDto(v.getOrderId(), v.getCustomerId(), v.getProductId(),
                        v.getQuantity(), v.getStatus(), v.getReason(), v.getTotal()))
                .orElse(null);
    }

    @QueryHandler
    public StatsDto handle(com.study.app.query.FindStats q) {
        return new StatsDto(orders.countConfirmed(), orders.countRejected(), orders.revenue());
    }
}
