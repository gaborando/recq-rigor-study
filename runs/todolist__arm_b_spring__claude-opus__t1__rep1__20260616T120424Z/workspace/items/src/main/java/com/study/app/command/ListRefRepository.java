package com.study.app.command;

import com.study.app.domain.ListRef;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ListRefRepository extends JpaRepository<ListRef, UUID> {

    /** Per-list lock: serializes mutations + completeness computation for one list. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ListRef r where r.listId = :id")
    Optional<ListRef> findByIdForUpdate(@Param("id") UUID id);
}
