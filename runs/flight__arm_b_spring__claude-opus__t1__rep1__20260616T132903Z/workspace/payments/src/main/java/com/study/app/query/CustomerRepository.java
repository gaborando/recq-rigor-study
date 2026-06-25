package com.study.app.query;

import com.study.app.domain.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, String> {

    /** Lock the customer row so concurrent charges against one balance serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> lockById(@Param("id") String id);

    /** Atomic credit — concurrent deposits can never lose an update. */
    @Modifying
    @Query("update Customer c set c.balance = c.balance + :amount where c.id = :id")
    int credit(@Param("id") String id, @Param("amount") long amount);
}
