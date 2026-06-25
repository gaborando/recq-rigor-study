package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.OrderStatsQuery;
import com.study.app.domain.view.StatsView;

@Projection
public class StatsProjection {

    private static final String KEY = "GLOBAL";
    private final StatsRepository repository;

    public StatsProjection(StatsRepository repository) {
        this.repository = repository;
    }

    @QueryHandler
    Single<StatsView> query(OrderStatsQuery q) {
        var s = repository.findById(KEY).orElse(null);
        if (s == null) return Single.of(new StatsView(0, 0, 0));
        return Single.of(new StatsView(s.getConfirmed(), s.getRejected(), s.getRevenue()));
    }
}
