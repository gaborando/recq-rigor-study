package com.study.app.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :units WHERE p.id = :id")
    int addStock(@Param("id") UUID id, @Param("units") int units);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :q WHERE p.id = :id AND p.stock >= :q")
    int reserveStock(@Param("id") UUID id, @Param("q") int q);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :q WHERE p.id = :id")
    int releaseStock(@Param("id") UUID id, @Param("q") int q);
}
