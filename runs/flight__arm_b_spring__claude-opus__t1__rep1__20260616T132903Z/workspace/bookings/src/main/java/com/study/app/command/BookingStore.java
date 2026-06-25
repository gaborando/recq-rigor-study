package com.study.app.command;

import com.study.app.domain.Booking;
import com.study.app.domain.Notification;
import com.study.app.query.BookingRepository;
import com.study.app.query.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** All transactional database work for bookings, isolated so @Transactional is honoured. */
@Component
public class BookingStore {

    private final BookingRepository bookings;
    private final NotificationRepository notifications;

    public BookingStore(BookingRepository bookings, NotificationRepository notifications) {
        this.bookings = bookings;
        this.notifications = notifications;
    }

    /** Insert the booking, or return the existing one — idempotent on bookingId. */
    @Transactional
    public Created createOrGet(String bookingId, String customerId, String flightId, String seat) {
        int inserted = bookings.insertIfAbsent(bookingId, customerId, flightId, seat, Instant.now());
        Booking b = bookings.findById(bookingId).orElseThrow();
        return new Created(b, inserted > 0);
    }

    public Optional<Booking> find(String bookingId) {
        return bookings.findById(bookingId);
    }

    public List<Booking> pending() {
        return bookings.findByStatus(Booking.PENDING);
    }

    /**
     * Compare-and-set decision. Only a PENDING booking is decided, and only once;
     * the single winner also writes the one-per-booking notification.
     */
    @Transactional
    public boolean decide(String bookingId, String status, String reason, Long total) {
        int updated = bookings.decide(bookingId, status, reason, total);
        if (updated == 0) return false;
        Booking b = bookings.findById(bookingId).orElseThrow();
        try {
            notifications.save(new Notification(bookingId, b.getCustomerId(), status, reason));
        } catch (DataIntegrityViolationException alreadyNotified) {
            // unreachable in practice (CAS guarantees a single winner) — defensive only
        }
        return true;
    }

    public List<Notification> notificationsFor(String customerId) {
        return notifications.findByCustomerId(customerId);
    }

    public Stats stats() {
        return new Stats(
                bookings.countByStatus(Booking.CONFIRMED),
                bookings.countByStatus(Booking.REJECTED),
                bookings.totalRevenue());
    }

    public record Created(Booking booking, boolean created) {}
    public record Stats(long confirmed, long rejected, long revenue) {}
}
