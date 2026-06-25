package com.study.app.command;

import com.study.app.domain.NotifOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotifOutboxRepository extends JpaRepository<NotifOutbox, Long> {
    List<NotifOutbox> findTop200BySentFalseOrderByIdAsc();
}
