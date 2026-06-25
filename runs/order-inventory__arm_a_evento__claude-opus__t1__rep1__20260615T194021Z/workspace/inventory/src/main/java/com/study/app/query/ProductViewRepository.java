package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProductViewRepository extends JpaRepository<ProductViewEntity, String> {

    @Modifying
    @Transactional
    @Query("update ProductViewEntity p set p.stock = p.stock + :delta where p.id = :id")
    void adjustStock(@Param("id") String id, @Param("delta") long delta);
}
