package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Single;
import com.study.app.domain.query.GetStatsQuery;
import com.study.app.domain.view.StatsView;
import com.study.app.query.repository.OrderRepository;

@Projection
public class StatsProjection {

    private final OrderRepository orderRepository;

    public StatsProjection(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @QueryHandler
    Single<StatsView> query(GetStatsQuery q) {
        long confirmed = orderRepository.countByStatus("CONFIRMED");
        long rejected = orderRepository.countByStatus("REJECTED");
        long revenue = orderRepository.sumConfirmedRevenue();
        return Single.of(new StatsView(confirmed, rejected, revenue));
    }
}
