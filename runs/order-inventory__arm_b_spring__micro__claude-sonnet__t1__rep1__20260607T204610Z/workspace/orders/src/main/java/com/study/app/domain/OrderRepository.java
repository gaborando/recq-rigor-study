package com.study.app.domain;

import com.study.app.query.StatsView;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT new com.study.app.query.StatsView(" +
           "  SUM(CASE WHEN o.status = 'CONFIRMED' THEN 1L ELSE 0L END), " +
           "  SUM(CASE WHEN o.status = 'REJECTED' THEN 1L ELSE 0L END), " +
           "  COALESCE(SUM(CASE WHEN o.status = 'CONFIRMED' THEN CAST(o.total AS long) ELSE 0L END), 0L)) " +
           "FROM Order o")
    StatsView computeStats();
}
