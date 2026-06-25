package com.study.app.command;

import com.study.app.domain.ListRef;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ListRefRepository extends JpaRepository<ListRef, UUID> {}
