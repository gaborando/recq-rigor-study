package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ListRepository extends JpaRepository<ListEntity, String> {

    long countByStatus(String status);
}
