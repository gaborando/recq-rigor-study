package com.study.app.command;

import com.study.app.domain.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    /** Lock the row for the check-then-act reserve window (serializes per product). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") String id);

    /** Atomic, lost-update-free increment used by restock and release compensation. */
    @Modifying(clearAutomatically = true)
    @Query("update Product p set p.available = p.available + :units where p.id = :id")
    int addAvailable(@Param("id") String id, @Param("units") long units);
}
