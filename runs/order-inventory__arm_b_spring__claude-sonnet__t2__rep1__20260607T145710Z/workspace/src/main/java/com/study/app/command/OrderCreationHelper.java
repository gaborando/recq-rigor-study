package com.study.app.command;

import com.study.app.domain.Order;
import com.study.app.domain.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class OrderCreationHelper {

    @Autowired
    private OrderRepository orderRepository;

    /**
     * Inserts a new PENDING order in its own committed transaction.
     * Throws DataIntegrityViolationException if orderId already exists — let the caller handle it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order insert(UUID orderId, UUID customerId, UUID productId, int quantity) {
        return orderRepository.saveAndFlush(new Order(orderId, customerId, productId, quantity));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Order findById(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow();
    }
}
