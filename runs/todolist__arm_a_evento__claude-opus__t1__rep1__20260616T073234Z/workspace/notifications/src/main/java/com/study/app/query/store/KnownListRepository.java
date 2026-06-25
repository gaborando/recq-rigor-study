package com.study.app.query.store;

import org.springframework.data.jpa.repository.JpaRepository;

public interface KnownListRepository extends JpaRepository<KnownListEntity, String> {
}
