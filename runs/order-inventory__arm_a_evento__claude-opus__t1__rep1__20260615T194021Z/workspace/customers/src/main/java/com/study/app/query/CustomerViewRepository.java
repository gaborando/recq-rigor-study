package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface CustomerViewRepository extends JpaRepository<CustomerViewEntity, String> {

    @Modifying
    @Transactional
    @Query("update CustomerViewEntity c set c.balance = c.balance + :delta where c.id = :id")
    void adjustBalance(@Param("id") String id, @Param("delta") long delta);
}
