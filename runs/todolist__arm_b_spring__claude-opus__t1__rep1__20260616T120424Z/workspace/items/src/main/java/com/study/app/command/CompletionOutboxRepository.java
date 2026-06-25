package com.study.app.command;

import com.study.app.domain.CompletionOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompletionOutboxRepository extends JpaRepository<CompletionOutbox, Long> {
    List<CompletionOutbox> findTop200BySentFalseOrderByIdAsc();
}
