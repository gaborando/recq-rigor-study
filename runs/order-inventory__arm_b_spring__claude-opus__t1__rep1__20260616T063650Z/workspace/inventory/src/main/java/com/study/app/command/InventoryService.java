package com.study.app.command;

import com.study.app.domain.Product;
import com.study.app.domain.Reservation;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Write side for inventory: product creation/restock and the reserve / release /
 * confirm operations that back the order saga. All saga operations are keyed by
 * orderId so they are idempotent under retries, and stock is mutated under a row
 * lock so concurrent orders never oversell.
 */
@Service
public class InventoryService {

    /** Outcome of a reserve attempt, returned to the orders service. */
    public enum ReserveOutcome { RESERVED, OUT_OF_STOCK, UNKNOWN_PRODUCT }

    public record ReserveResult(ReserveOutcome outcome, int unitPrice) {}

    private final ProductRepository products;
    private final ReservationRepository reservations;

    public InventoryService(ProductRepository products, ReservationRepository reservations) {
        this.products = products;
        this.reservations = reservations;
    }

    @Transactional
    public Product createProduct(String name, int unitPrice, int stock) {
        return products.save(new Product(UUID.randomUUID().toString(), name, unitPrice, stock));
    }

    /** @return true if applied, false if the product is unknown. */
    @Transactional
    public boolean restock(String productId, int units) {
        return products.addAvailable(productId, units) == 1;
    }

    @Transactional(readOnly = true)
    public Product find(String productId) {
        return products.findById(productId).orElse(null);
    }

    /**
     * Reserve {@code quantity} units for {@code orderId}. Idempotent: a repeat call
     * with the same orderId returns the original outcome without touching stock.
     */
    @Transactional
    public ReserveResult reserve(String orderId, String productId, int quantity) {
        Reservation existing = reservations.findById(orderId).orElse(null);
        if (existing != null) {
            return toResult(existing);
        }

        // Read the product ONLY through the locking query so the value is the
        // freshly-locked row state, not a stale persistence-context copy. Loading it
        // unlocked first and then re-fetching FOR UPDATE would return the cached
        // (pre-lock) available and defeat the lock — that path oversells.
        Product locked = products.findByIdForUpdate(productId).orElse(null);
        if (locked == null) {
            return persist(new Reservation(orderId, productId, quantity, 0,
                    Reservation.Status.REJECTED_UNKNOWN));
        }

        // Re-check after acquiring the lock: a concurrent same-orderId reserve may
        // have committed while we waited for the lock (READ_COMMITTED sees it now).
        existing = reservations.findById(orderId).orElse(null);
        if (existing != null) {
            return toResult(existing);
        }

        if (locked.getAvailable() >= quantity) {
            locked.setAvailable(locked.getAvailable() - quantity);
            products.save(locked);
            return persist(new Reservation(orderId, productId, quantity, locked.getUnitPrice(),
                    Reservation.Status.RESERVED));
        }
        return persist(new Reservation(orderId, productId, quantity, locked.getUnitPrice(),
                Reservation.Status.REJECTED_OOS));
    }

    /** Compensation: add the reserved units back, exactly once. Idempotent. */
    @Transactional
    public void release(String orderId) {
        if (reservations.markReleased(orderId) == 1) {
            Reservation r = reservations.findById(orderId).orElseThrow();
            products.addAvailable(r.getProductId(), r.getQuantity());
        }
    }

    /** Consume the reservation (order confirmed). Stock stays decremented. Idempotent. */
    @Transactional
    public void confirm(String orderId) {
        reservations.markConfirmed(orderId);
    }

    private ReserveResult persist(Reservation r) {
        try {
            reservations.saveAndFlush(r);
            return toResult(r);
        } catch (DataIntegrityViolationException dup) {
            // Concurrent same-orderId reserve won the race; return its committed outcome.
            return toResult(reservations.findById(r.getOrderId()).orElseThrow());
        }
    }

    private ReserveResult toResult(Reservation r) {
        return switch (r.getStatus()) {
            case RESERVED, CONFIRMED, RELEASED ->
                    new ReserveResult(ReserveOutcome.RESERVED, r.getUnitPrice());
            case REJECTED_OOS ->
                    new ReserveResult(ReserveOutcome.OUT_OF_STOCK, r.getUnitPrice());
            case REJECTED_UNKNOWN ->
                    new ReserveResult(ReserveOutcome.UNKNOWN_PRODUCT, r.getUnitPrice());
        };
    }
}
