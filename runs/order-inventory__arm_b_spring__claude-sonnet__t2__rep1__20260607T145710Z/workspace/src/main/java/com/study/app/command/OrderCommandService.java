package com.study.app.command;

import com.study.app.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class OrderCommandService {

    @Autowired
    private OrderCreationHelper creationHelper;
    @Autowired
    private OrderProcessor processor;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CustomerRepository customerRepository;

    public Order placeOrder(UUID orderId, UUID customerId, UUID productId, int quantity) {
        if (!productRepository.existsById(productId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "product not found");
        }
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer not found");
        }

        Order order;
        try {
            order = creationHelper.insert(orderId, customerId, productId, quantity);
        } catch (DataIntegrityViolationException e) {
            // orderId already exists — idempotent replay
            order = creationHelper.findById(orderId);
        }

        if (order.getStatus() == OrderStatus.PENDING) {
            try {
                processor.process(orderId);
            } catch (ObjectOptimisticLockingFailureException e) {
                // another concurrent thread won the processing race — fine
            }
        }

        return orderRepository.findById(orderId).orElseThrow();
    }
}
