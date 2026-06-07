package com.study.app.command;

import com.study.app.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@Service
public class OrderCancellationService {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Transactional
    public void cancel(UUID orderId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "order not found"));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            // PENDING, REJECTED, or already CANCELLED: no-op
            return;
        }

        // Atomic CAS: transition CONFIRMED -> CANCELLED exactly once
        int updated = orderRepository.cancelIfConfirmed(orderId);
        if (updated == 0) {
            // Lost the race to a concurrent cancel — already cancelled
            return;
        }

        // We won the race: refund balance, restore stock, emit CANCELLED notification
        customerRepository.addBalance(order.getCustomerId(), order.getTotal());
        productRepository.release(order.getProductId(), order.getQuantity());

        try {
            notificationRepository.saveAndFlush(
                new Notification(orderId, order.getCustomerId(), OrderStatus.CANCELLED, null)
            );
        } catch (DataIntegrityViolationException e) {
            // idempotent: CANCELLED notification already exists
        }
    }
}
