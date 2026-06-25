package com.study.app.domain.query;

import com.evento.common.modeling.messaging.payload.Query;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.view.StatsView;

public class GetStatsQuery extends Query<Single<StatsView>> {
    public GetStatsQuery() {}
}
