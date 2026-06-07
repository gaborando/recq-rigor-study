package com.study.app.query.repository;

import com.study.app.query.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    List<NotificationEntity> findByCustomerId(String customerId);

    boolean existsByOrderId(String orderId);
}
