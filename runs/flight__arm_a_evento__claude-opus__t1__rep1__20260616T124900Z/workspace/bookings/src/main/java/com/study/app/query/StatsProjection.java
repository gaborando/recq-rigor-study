package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetStatsQuery;
import com.study.app.domain.view.StatsView;

@Projection
public class StatsProjection {

    private final StatsRepository stats;

    public StatsProjection(StatsRepository stats) {
        this.stats = stats;
    }

    @QueryHandler
    Single<StatsView> query(GetStatsQuery q) {
        return stats.findById(StatsProjector.GLOBAL)
                .map(s -> Single.of(new StatsView(s.getConfirmed(), s.getRejected(), s.getRevenue())))
                .orElseGet(() -> Single.of(new StatsView(0, 0, 0)));
    }
}
