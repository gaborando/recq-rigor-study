package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OrderViewRepository extends JpaRepository<OrderView, String> {

    @Query("select count(o) from OrderView o where o.status = 'CONFIRMED'")
    long countConfirmed();

    @Query("select count(o) from OrderView o where o.status = 'REJECTED'")
    long countRejected();

    @Query("select coalesce(sum(o.total), 0) from OrderView o where o.status = 'CONFIRMED'")
    long revenue();
}
