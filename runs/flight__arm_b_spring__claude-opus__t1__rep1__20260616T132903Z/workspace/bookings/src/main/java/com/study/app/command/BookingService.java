package com.study.app.command;

import com.study.app.domain.Booking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Orchestrates the booking saga over HTTP: HOLD seat -> CHARGE customer ->
 * CONFIRM, with compensation (release / refund) on failure. Every external step
 * is idempotent and keyed by bookingId, so re-running the saga is harmless — which
 * is what makes the async submit, retries and crash-recovery safe.
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingStore store;
    private final PeerGateway peer;
    private final Executor sagaExecutor;

    public BookingService(BookingStore store, PeerGateway peer, Executor sagaExecutor) {
        this.store = store;
        this.peer = peer;
        this.sagaExecutor = sagaExecutor;
    }

    /** Accept a booking (idempotent), kick off async processing for fresh ones. */
    public Booking book(String bookingId, String customerId, String flightId, String seat) {
        BookingStore.Created c = store.createOrGet(bookingId, customerId, flightId, seat);
        if (c.created()) {
            sagaExecutor.execute(() -> safeProcess(bookingId));
        }
        return c.booking();
    }

    private void safeProcess(String bookingId) {
        try {
            process(bookingId);
        } catch (RuntimeException e) {
            // leave PENDING; the recovery sweeper will retry it
            log.warn("saga failed for {}, will retry: {}", bookingId, e.toString());
        }
    }

    public void process(String bookingId) {
        Booking b = store.find(bookingId).orElse(null);
        if (b == null || !Booking.PENDING.equals(b.getStatus())) return;

        // 1. HOLD the seat (the distributed lock)
        Map<String, Object> hold = peer.hold(b.getFlightId(), b.getSeat(), bookingId);
        String holdResult = (String) hold.get("result");
        if (!"HELD".equals(holdResult)) {
            // TAKEN, or an unknown flight/seat — nothing charged
            store.decide(bookingId, Booking.REJECTED, "SEAT_TAKEN", null);
            return;
        }
        long price = ((Number) hold.get("seatPrice")).longValue();

        // 2. CHARGE the customer
        String charge = peer.charge(bookingId, b.getCustomerId(), price);
        if (!"SUCCESS".equals(charge)) {
            // compensation: release the held seat; no money taken
            peer.release(b.getFlightId(), b.getSeat(), bookingId);
            store.decide(bookingId, Booking.REJECTED, "INSUFFICIENT_FUNDS", null);
            return;
        }

        // 3. CONFIRM ownership of the held seat
        boolean confirmed = peer.confirm(b.getFlightId(), b.getSeat(), bookingId);
        if (confirmed) {
            store.decide(bookingId, Booking.CONFIRMED, null, price);
        } else {
            // hold lost before confirm (e.g. expiry): undo the charge, no residual effect
            peer.refund(bookingId);
            peer.release(b.getFlightId(), b.getSeat(), bookingId);
            store.decide(bookingId, Booking.REJECTED, "SEAT_TAKEN", null);
        }
    }

    /** Recover bookings left PENDING by a crash, restart, or transient failure. */
    @Scheduled(fixedDelayString = "${BOOKING_RECOVERY_MS:3000}")
    public void recoverStuck() {
        Instant cutoff = Instant.now().minus(3, ChronoUnit.SECONDS);
        for (Booking b : store.pending()) {
            if (b.getCreatedAt() != null && b.getCreatedAt().isBefore(cutoff)) {
                sagaExecutor.execute(() -> safeProcess(b.getBookingId()));
            }
        }
    }
}
