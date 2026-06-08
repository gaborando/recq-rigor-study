package com.study.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Modifying
    @Query("UPDATE Customer c SET c.balance = c.balance + :amount WHERE c.id = :id")
    int addBalance(@Param("id") UUID id, @Param("amount") int amount);

    @Modifying
    @Query("UPDATE Customer c SET c.balance = c.balance - :amount WHERE c.id = :id AND c.balance >= :amount")
    int charge(@Param("id") UUID id, @Param("amount") int amount);
}
