package com.study.app.query;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatsRepository extends JpaRepository<StatsEntity, String> {
}
