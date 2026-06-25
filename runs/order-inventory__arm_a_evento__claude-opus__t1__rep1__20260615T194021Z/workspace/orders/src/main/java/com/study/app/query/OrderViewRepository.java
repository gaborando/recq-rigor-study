package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderViewRepository extends JpaRepository<OrderViewEntity, String> {
}
