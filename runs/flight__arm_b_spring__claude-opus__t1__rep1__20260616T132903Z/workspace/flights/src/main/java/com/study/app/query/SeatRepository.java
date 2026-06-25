package com.study.app.query;

import com.study.app.domain.Seat;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    List<Seat> findByFlightIdOrderByIdx(String flightId);

    /** Acquire the per-seat row lock — this serialises all concurrent holders of a seat. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.flightId = :flightId and s.label = :label")
    Optional<Seat> lockByFlightAndLabel(@Param("flightId") String flightId, @Param("label") String label);

    @Query("select s from Seat s where s.status = 'HELD' and s.holdExpiresAt < :now")
    List<Seat> findExpiredHolds(@Param("now") Instant now);
}
