package com.study.app.query;

import com.study.app.domain.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class StatsQueryService {

    @Autowired
    private OrderRepository orderRepository;

    public Map<String, Long> getStats() {
        return Map.of(
            "confirmed", orderRepository.countConfirmed(),
            "rejected", orderRepository.countRejected(),
            "cancelled", orderRepository.countCancelled(),
            "revenue", orderRepository.sumRevenue()
        );
    }
}
