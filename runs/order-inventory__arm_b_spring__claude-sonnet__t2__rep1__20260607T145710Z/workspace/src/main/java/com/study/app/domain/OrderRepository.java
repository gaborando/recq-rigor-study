package com.study.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("select coalesce(count(o),0) from Order o where o.status = 'CONFIRMED'")
    long countConfirmed();

    @Query("select coalesce(count(o),0) from Order o where o.status = 'REJECTED'")
    long countRejected();

    @Query("select coalesce(count(o),0) from Order o where o.status = 'CANCELLED'")
    long countCancelled();

    @Query("select coalesce(sum(o.total),0) from Order o where o.status = 'CONFIRMED'")
    long sumRevenue();

    @Modifying
    @Query(value = "UPDATE orders SET status = 'CANCELLED' WHERE order_id = :id AND status = 'CONFIRMED'", nativeQuery = true)
    int cancelIfConfirmed(@Param("id") UUID id);
}
