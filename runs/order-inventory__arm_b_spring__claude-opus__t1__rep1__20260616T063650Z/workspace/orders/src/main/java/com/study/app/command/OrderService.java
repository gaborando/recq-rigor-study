package com.study.app.command;

import com.study.app.domain.OrderEntity;
import com.study.app.domain.OrderEntity.Status;
import com.study.app.query.Stats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Order lifecycle + the reserve -> charge -> confirm/reject saga over REST.
 *
 * <p>Correctness model:
 * <ul>
 *   <li><b>Idempotent creation</b>: orderId is the PK; a raced/retried POST cannot
 *       create a second order.</li>
 *   <li><b>Replayable saga</b>: every peer step (reserve/charge/release/confirm) is
 *       keyed by orderId, and the decision is committed by a guarded
 *       PENDING-&gt;decided UPDATE. So processing the same order more than once
 *       (async + recovery, or after a crash) produces exactly one effect.</li>
 *   <li><b>Exactly-once notification</b>: tracked by a {@code notified} flag and
 *       deduplicated by the customers service; retried until delivered.</li>
 * </ul>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final InventoryClient inventory;
    private final CustomerClient customers;

    public OrderService(OrderRepository orders, InventoryClient inventory, CustomerClient customers) {
        this.orders = orders;
        this.inventory = inventory;
        this.customers = customers;
    }

    /**
     * Idempotent create. Returns the existing order on a duplicate orderId.
     *
     * <p>Deliberately NOT wrapped in an outer @Transactional: the insert runs in
     * the repository's own transaction, so a duplicate-key violation (concurrent
     * same-orderId POSTs) rolls back only that inner transaction. We then re-read
     * the winning row in a fresh transaction. Wrapping the catch in the same
     * transaction would mark it rollback-only and fail the commit.
     */
    public OrderEntity create(String orderId, String customerId, String productId, int quantity) {
        OrderEntity existing = orders.findById(orderId).orElse(null);
        if (existing != null) return existing;
        try {
            OrderEntity o = new OrderEntity(orderId, customerId, productId, quantity);
            orders.saveAndFlush(o);
            return o;
        } catch (DataIntegrityViolationException dup) {
            return orders.findById(orderId).orElseThrow();
        }
    }

    @Transactional(readOnly = true)
    public OrderEntity find(String orderId) {
        return orders.findById(orderId).orElse(null);
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        return orders.stats();
    }

    /** Fire-and-forget kick of order processing right after creation. */
    @Async
    public void processAsync(String orderId) {
        try {
            process(orderId);
        } catch (Exception e) {
            // Transient (peer down, etc.) — the recovery scan retries.
            log.debug("async processing of {} failed, will retry: {}", orderId, e.toString());
        }
    }

    /**
     * Drive an order to a decision and deliver its notification. Safe to call any
     * number of times concurrently: each step is idempotent.
     */
    public void process(String orderId) {
        OrderEntity o = orders.findById(orderId).orElse(null);
        if (o == null) return;

        if (o.getStatus() == Status.PENDING) {
            runSaga(o);
            o = orders.findById(orderId).orElse(null);
            if (o == null) return;
        }

        if (o.getStatus() != Status.PENDING && !o.isNotified()) {
            customers.notify(o.getOrderId(), o.getCustomerId(), o.getStatus().name(), o.getReason());
            orders.markNotified(o.getOrderId());
        }
    }

    private void runSaga(OrderEntity o) {
        // 1. reserve stock
        InventoryClient.ReserveResult reserve =
                inventory.reserve(o.getOrderId(), o.getProductId(), o.getQuantity());
        if (reserve.outcome() != InventoryClient.ReserveOutcome.RESERVED) {
            // OUT_OF_STOCK or UNKNOWN_PRODUCT — nothing charged, nothing to release.
            orders.decide(o.getOrderId(), Status.REJECTED, "OUT_OF_STOCK", null);
            return;
        }

        // 2. charge funds
        long total = (long) o.getQuantity() * reserve.unitPrice();
        CustomerClient.ChargeOutcome charge =
                customers.charge(o.getOrderId(), o.getCustomerId(), total);
        if (charge != CustomerClient.ChargeOutcome.CHARGED) {
            // INSUFFICIENT_FUNDS or UNKNOWN_CUSTOMER — compensate the reservation.
            inventory.release(o.getOrderId());
            orders.decide(o.getOrderId(), Status.REJECTED, "INSUFFICIENT_FUNDS", null);
            return;
        }

        // 3. confirm
        inventory.confirm(o.getOrderId());
        orders.decide(o.getOrderId(), Status.CONFIRMED, null, total);
    }

    /**
     * Recovery scan: drives any unsettled order (undecided, or decided but not yet
     * notified) forward. Handles crash recovery and transient peer failures.
     */
    @Scheduled(fixedDelayString = "${orders.recovery.interval-ms:1000}", initialDelay = 1500)
    public void recover() {
        for (String id : orders.findUnsettledIds()) {
            try {
                process(id);
            } catch (Exception e) {
                log.debug("recovery of {} failed, will retry: {}", id, e.toString());
            }
        }
    }
}
