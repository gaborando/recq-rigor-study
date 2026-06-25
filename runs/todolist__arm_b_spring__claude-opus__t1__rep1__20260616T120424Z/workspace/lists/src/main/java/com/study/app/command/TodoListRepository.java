package com.study.app.command;

import com.study.app.domain.TodoList;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TodoListRepository extends JpaRepository<TodoList, UUID> {

    /** Lock the list row so completion reports for one list are applied serially. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from TodoList l where l.id = :id")
    Optional<TodoList> findByIdForUpdate(@Param("id") UUID id);

    @Query("select count(l) from TodoList l where l.status = 'ACTIVE'")
    long countActive();

    @Query("select count(l) from TodoList l where l.status = 'COMPLETED'")
    long countCompleted();
}
