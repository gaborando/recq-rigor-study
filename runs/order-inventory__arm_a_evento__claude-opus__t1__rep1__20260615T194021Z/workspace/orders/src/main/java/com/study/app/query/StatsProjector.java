package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.OrderConfirmedEvent;
import com.study.app.domain.event.OrderRejectedEvent;

/**
 * Maintains aggregate statistics over all orders. Single active consumer +
 * dedupe means each decision event is counted exactly once.
 */
@Projector(version = 1)
public class StatsProjector {

    private static final String KEY = "GLOBAL";
    private final StatsRepository repository;

    public StatsProjector(StatsRepository repository) {
        this.repository = repository;
    }

    private StatsEntity load() {
        return repository.findById(KEY).orElseGet(() -> new StatsEntity(KEY, 0, 0, 0));
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderConfirmedEvent e) {
        var s = load();
        s.setConfirmed(s.getConfirmed() + 1);
        s.setRevenue(s.getRevenue() + e.getTotal());
        repository.save(s);
    }

    @EventHandler(retry = 10, retryDelay = 500)
    void on(OrderRejectedEvent e) {
        var s = load();
        s.setRejected(s.getRejected() + 1);
        repository.save(s);
    }
}
