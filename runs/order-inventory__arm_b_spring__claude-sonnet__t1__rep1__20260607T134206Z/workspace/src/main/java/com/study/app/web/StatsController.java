package com.study.app.web;

import com.study.app.query.StatsQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/stats")
public class StatsController {

    @Autowired
    private StatsQueryService statsQueryService;

    @GetMapping("/orders")
    public Map<String, Long> getStats() {
        return statsQueryService.getStats();
    }
}
