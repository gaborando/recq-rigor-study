package com.study.app.web;

import com.study.app.query.FindStats;
import com.study.app.query.StatsDto;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stats")
public class StatsController {

    private final QueryGateway queryGateway;

    public StatsController(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @GetMapping("/orders")
    public StatsDto orders() {
        return queryGateway.query(new FindStats(), ResponseTypes.instanceOf(StatsDto.class)).join();
    }
}
