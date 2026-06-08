package com.study.app.web;

import com.study.app.domain.OrderRepository;
import com.study.app.query.StatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final OrderRepository orders;

    public StatsController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping("/stats/orders")
    public StatsView stats() {
        return orders.computeStats();
    }
}
