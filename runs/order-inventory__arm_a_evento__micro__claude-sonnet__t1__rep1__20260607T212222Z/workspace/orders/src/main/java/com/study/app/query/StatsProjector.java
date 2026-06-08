package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

@Projector(version = 1)
public class StatsProjector {

    @EventHandler
    void on(OrderConfirmedEvent e) {
        OrderStatsStore.incrementConfirmed();
        OrderStatsStore.addRevenue(e.getTotal());
    }

    @EventHandler
    void on(OrderRejectedEvent e) {
        OrderStatsStore.incrementRejected();
    }
}
