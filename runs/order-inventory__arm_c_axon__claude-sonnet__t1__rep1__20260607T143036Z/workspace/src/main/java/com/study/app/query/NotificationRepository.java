package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<NotificationView, Long> {
    List<NotificationView> findByCustomerId(String customerId);
}
