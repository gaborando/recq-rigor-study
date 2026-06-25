package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.BookingConfirmedEvent;
import com.study.app.domain.event.BookingRejectedEvent;
import org.springframework.transaction.annotation.Transactional;

@Projector(version = 1)
public class StatsProjector {

    static final String GLOBAL = "GLOBAL";

    private final StatsRepository stats;

    public StatsProjector(StatsRepository stats) {
        this.stats = stats;
    }

    @EventHandler
    @Transactional
    void on(BookingConfirmedEvent e) {
        StatsEntity s = stats.findById(GLOBAL).orElseGet(() -> new StatsEntity(GLOBAL));
        s.setConfirmed(s.getConfirmed() + 1);
        s.setRevenue(s.getRevenue() + e.getTotal());
        stats.save(s);
    }

    @EventHandler
    @Transactional
    void on(BookingRejectedEvent e) {
        StatsEntity s = stats.findById(GLOBAL).orElseGet(() -> new StatsEntity(GLOBAL));
        s.setRejected(s.getRejected() + 1);
        stats.save(s);
    }
}
