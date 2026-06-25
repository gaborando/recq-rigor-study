package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRowRepository extends JpaRepository<NotificationRow, String> {
    List<NotificationRow> findByCustomerId(String customerId);
}
