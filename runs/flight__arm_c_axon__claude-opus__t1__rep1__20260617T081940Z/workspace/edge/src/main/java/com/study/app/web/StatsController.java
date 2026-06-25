package com.study.app.web;

import com.study.app.api.query.Queries.FindStats;
import com.study.app.api.query.Views.StatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StatsController {

    private final Gateways gateways;

    public StatsController(Gateways gateways) {
        this.gateways = gateways;
    }

    @GetMapping("/stats/bookings")
    public StatsView stats() {
        StatsView s = gateways.querySingle(new FindStats(), StatsView.class);
        return s != null ? s : new StatsView(0, 0, 0);
    }
}
