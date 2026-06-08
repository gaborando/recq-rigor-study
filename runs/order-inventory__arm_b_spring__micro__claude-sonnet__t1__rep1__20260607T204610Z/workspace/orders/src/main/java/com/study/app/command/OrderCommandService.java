package com.study.app.command;

import com.study.app.domain.Order;
import com.study.app.domain.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

    private final OrderRepository orders;
    private final InventoryClient inventoryClient;
    private final CustomersClient customersClient;
    private final OrderCommandService self;

    public OrderCommandService(
            OrderRepository orders,
            InventoryClient inventoryClient,
            CustomersClient customersClient,
            @Lazy OrderCommandService self) {
        this.orders = orders;
        this.inventoryClient = inventoryClient;
        this.customersClient = customersClient;
        this.self = self;
    }

    // No outer @Transactional — tryInsert uses REQUIRES_NEW so its failure stays isolated
    public Order create(UUID orderId, UUID customerId, UUID productId, int quantity) {
        Order existing = orders.findById(orderId).orElse(null);
        if (existing != null) {
            return existing;
        }
        Order order;
        try {
            order = self.tryInsert(orderId, customerId, productId, quantity);
        } catch (DataIntegrityViolationException dup) {
            return orders.findById(orderId).orElseThrow();
        }
        self.processAsync(orderId);
        return order;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order tryInsert(UUID orderId, UUID customerId, UUID productId, int quantity) {
        return orders.saveAndFlush(new Order(orderId, customerId, productId, quantity));
    }

    @Async
    public void processAsync(UUID orderId) {
        try {
            self.processSaga(orderId);
        } catch (Exception e) {
            log.error("Saga failed for order {}: {}", orderId, e.getMessage(), e);
        }
    }

    @Transactional
    public void processSaga(UUID orderId) {
        Order order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (!"PENDING".equals(order.getStatus())) {
            return;
        }

        InventoryClient.ProductInfo product;
        try {
            product = inventoryClient.getProduct(order.getProductId());
        } catch (Exception e) {
            log.warn("Cannot get product for order {}: {}", orderId, e.getMessage());
            order.reject("OUT_OF_STOCK");
            orders.save(order);
            customersClient.notify(order.getCustomerId(), orderId, "REJECTED", "OUT_OF_STOCK");
            return;
        }

        int total = order.getQuantity() * product.unitPrice();

        boolean reserved;
        try {
            reserved = inventoryClient.reserve(order.getProductId(), orderId, order.getQuantity());
        } catch (Exception e) {
            log.warn("Reserve failed for order {}: {}", orderId, e.getMessage());
            order.reject("OUT_OF_STOCK");
            orders.save(order);
            customersClient.notify(order.getCustomerId(), orderId, "REJECTED", "OUT_OF_STOCK");
            return;
        }

        if (!reserved) {
            order.reject("OUT_OF_STOCK");
            orders.save(order);
            customersClient.notify(order.getCustomerId(), orderId, "REJECTED", "OUT_OF_STOCK");
            return;
        }

        boolean charged;
        try {
            charged = customersClient.charge(order.getCustomerId(), orderId, total);
        } catch (Exception e) {
            log.warn("Charge failed for order {}: {}", orderId, e.getMessage());
            try { inventoryClient.release(order.getProductId(), orderId, order.getQuantity()); } catch (Exception ex) { log.warn("Release also failed: {}", ex.getMessage()); }
            order.reject("INSUFFICIENT_FUNDS");
            orders.save(order);
            customersClient.notify(order.getCustomerId(), orderId, "REJECTED", "INSUFFICIENT_FUNDS");
            return;
        }

        if (!charged) {
            try {
                inventoryClient.release(order.getProductId(), orderId, order.getQuantity());
            } catch (Exception e) {
                log.warn("Release failed for order {}: {}", orderId, e.getMessage());
            }
            order.reject("INSUFFICIENT_FUNDS");
            orders.save(order);
            customersClient.notify(order.getCustomerId(), orderId, "REJECTED", "INSUFFICIENT_FUNDS");
            return;
        }

        order.confirm(total);
        orders.save(order);
        customersClient.notify(order.getCustomerId(), orderId, "CONFIRMED", null);
    }
}
