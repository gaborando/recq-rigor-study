package com.study.app.command;

import com.study.app.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    /** Flip RESERVED -> RELEASED exactly once; returns 1 only for the winning caller. */
    @Modifying(clearAutomatically = true)
    @Query("update Reservation r set r.status = 'RELEASED' " +
           "where r.orderId = :orderId and r.status = 'RESERVED'")
    int markReleased(@Param("orderId") String orderId);

    /** Flip RESERVED -> CONFIRMED (idempotent; no stock change). */
    @Modifying(clearAutomatically = true)
    @Query("update Reservation r set r.status = 'CONFIRMED' " +
           "where r.orderId = :orderId and r.status = 'RESERVED'")
    int markConfirmed(@Param("orderId") String orderId);
}
