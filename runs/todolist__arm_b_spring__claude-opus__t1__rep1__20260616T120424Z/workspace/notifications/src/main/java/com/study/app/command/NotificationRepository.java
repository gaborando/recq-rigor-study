package com.study.app.command;

import com.study.app.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByListIdOrderByTransitionSeqAsc(UUID listId);
}
