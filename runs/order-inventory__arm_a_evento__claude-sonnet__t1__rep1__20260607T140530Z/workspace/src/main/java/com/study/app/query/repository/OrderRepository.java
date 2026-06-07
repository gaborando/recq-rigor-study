package com.study.app.query.repository;

import com.study.app.query.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    long countByStatus(String status);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM OrderEntity o WHERE o.status = 'CONFIRMED'")
    long sumConfirmedRevenue();
}
