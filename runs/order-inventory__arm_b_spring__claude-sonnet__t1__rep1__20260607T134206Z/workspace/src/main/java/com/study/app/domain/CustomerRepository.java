package com.study.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Modifying
    @Query("update Customer c set c.balance = c.balance + :amount where c.id = :id")
    int addBalance(@Param("id") UUID id, @Param("amount") long amount);

    @Modifying
    @Query("update Customer c set c.balance = c.balance - :amount where c.id = :id and c.balance >= :amount")
    int charge(@Param("id") UUID id, @Param("amount") long amount);
}
