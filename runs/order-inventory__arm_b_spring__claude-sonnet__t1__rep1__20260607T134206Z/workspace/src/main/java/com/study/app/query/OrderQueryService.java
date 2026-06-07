package com.study.app.query;

import com.study.app.domain.Order;
import com.study.app.domain.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class OrderQueryService {

    @Autowired
    private OrderRepository orderRepository;

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));
    }
}
