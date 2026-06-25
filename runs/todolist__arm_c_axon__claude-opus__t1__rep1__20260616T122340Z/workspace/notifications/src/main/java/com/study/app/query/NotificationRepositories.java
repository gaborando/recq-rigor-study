package com.study.app.query;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ListProgressRepository extends JpaRepository<ListProgress, String> {
}

interface NotifItemStateRepository extends JpaRepository<NotifItemState, String> {

    long countByListId(String listId);

    long countByListIdAndCheckedTrue(String listId);
}

interface NotificationRecordRepository extends JpaRepository<NotificationRecord, Long> {

    List<NotificationRecord> findByListIdOrderBySeqAsc(String listId);

    boolean existsByListIdAndSeq(String listId, int seq);
}
