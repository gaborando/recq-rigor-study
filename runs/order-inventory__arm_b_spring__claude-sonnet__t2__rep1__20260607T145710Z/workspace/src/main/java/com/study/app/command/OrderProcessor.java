package com.study.app.command;

import com.study.app.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
public class OrderProcessor {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ApplicationEventPublisher publisher;

    @Transactional
    public void process(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        Product product = productRepository.findById(order.getProductId()).orElseThrow();
        long total = (long) order.getQuantity() * product.getUnitPrice();

        if (productRepository.reserve(order.getProductId(), order.getQuantity()) == 0) {
            order.reject("OUT_OF_STOCK");
            orderRepository.save(order);
            publisher.publishEvent(new OrderDecidedEvent(orderId, order.getCustomerId(), OrderStatus.REJECTED, "OUT_OF_STOCK"));
            return;
        }

        if (customerRepository.charge(order.getCustomerId(), total) == 0) {
            productRepository.release(order.getProductId(), order.getQuantity());
            order.reject("INSUFFICIENT_FUNDS");
            orderRepository.save(order);
            publisher.publishEvent(new OrderDecidedEvent(orderId, order.getCustomerId(), OrderStatus.REJECTED, "INSUFFICIENT_FUNDS"));
            return;
        }

        order.confirm(total);
        orderRepository.save(order);
        publisher.publishEvent(new OrderDecidedEvent(orderId, order.getCustomerId(), OrderStatus.CONFIRMED, null));
    }
}
