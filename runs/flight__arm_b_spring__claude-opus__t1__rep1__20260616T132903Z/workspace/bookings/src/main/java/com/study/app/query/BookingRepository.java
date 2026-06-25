package com.study.app.query;

import com.study.app.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, String> {

    List<Booking> findByStatus(String status);

    /** Idempotent insert: a duplicate bookingId is a no-op (returns 0 rows). */
    @Modifying
    @Query(value = "insert into bookings (booking_id, customer_id, flight_id, seat, status, created_at) "
            + "values (:id, :customerId, :flightId, :seat, 'PENDING', :createdAt) "
            + "on conflict (booking_id) do nothing", nativeQuery = true)
    int insertIfAbsent(@Param("id") String id, @Param("customerId") String customerId,
                       @Param("flightId") String flightId, @Param("seat") String seat,
                       @Param("createdAt") Instant createdAt);

    /** Compare-and-set decision: only a PENDING booking can be decided, and only once. */
    @Modifying(clearAutomatically = true)
    @Query("update Booking b set b.status = :status, b.reason = :reason, b.total = :total "
            + "where b.bookingId = :id and b.status = 'PENDING'")
    int decide(@Param("id") String id, @Param("status") String status,
               @Param("reason") String reason, @Param("total") Long total);

    long countByStatus(String status);

    @Query("select coalesce(sum(b.total), 0) from Booking b where b.status = 'CONFIRMED'")
    long totalRevenue();
}
