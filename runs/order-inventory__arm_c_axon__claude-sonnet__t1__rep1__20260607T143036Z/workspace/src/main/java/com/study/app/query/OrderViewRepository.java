package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderViewRepository extends JpaRepository<OrderView, String> {

    @Query("SELECT COUNT(o) FROM OrderView o WHERE o.status = 'CONFIRMED'")
    long countConfirmed();

    @Query("SELECT COUNT(o) FROM OrderView o WHERE o.status = 'REJECTED'")
    long countRejected();

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM OrderView o WHERE o.status = 'CONFIRMED'")
    long sumRevenue();
}
