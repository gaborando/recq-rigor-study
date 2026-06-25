package com.study.app.command;

import com.study.app.domain.OrderEntity;
import com.study.app.query.Stats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, String> {

    /**
     * Commit the decision exactly once: only the first transition out of PENDING
     * wins (returns 1). Status can never regress because the WHERE clause requires
     * status='PENDING'. {@code notified} is reset to false so the notification step
     * picks it up.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update OrderEntity o set o.status = :status, o.reason = :reason, o.total = :total, " +
           "o.notified = false where o.orderId = :orderId and o.status = 'PENDING'")
    int decide(@Param("orderId") String orderId,
               @Param("status") OrderEntity.Status status,
               @Param("reason") String reason,
               @Param("total") Long total);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("update OrderEntity o set o.notified = true where o.orderId = :orderId")
    int markNotified(@Param("orderId") String orderId);

    /** Orders still needing work: undecided, or decided but not yet notified. */
    @Query("select o.orderId from OrderEntity o where o.status = 'PENDING' or o.notified = false")
    List<String> findUnsettledIds();

    @Query("select new com.study.app.query.Stats(" +
           " coalesce(sum(case when o.status = 'CONFIRMED' then 1 else 0 end), 0)," +
           " coalesce(sum(case when o.status = 'REJECTED'  then 1 else 0 end), 0)," +
           " coalesce(sum(case when o.status = 'CONFIRMED' then o.total else 0 end), 0)) " +
           "from OrderEntity o")
    Stats stats();
}
