package com.study.app.web;

import com.study.app.query.FindOrderStats;
import com.study.app.query.OrderStats;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ExecutionException;

@RestController
public class StatsController {

    private final QueryGateway queryGateway;

    public StatsController(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @GetMapping("/stats/orders")
    public Map<String, Object> stats() throws ExecutionException, InterruptedException {
        OrderStats s = queryGateway.query(new FindOrderStats(), ResponseTypes.instanceOf(OrderStats.class)).get();
        return Map.of("confirmed", s.confirmed(), "rejected", s.rejected(), "revenue", s.revenue());
    }
}
