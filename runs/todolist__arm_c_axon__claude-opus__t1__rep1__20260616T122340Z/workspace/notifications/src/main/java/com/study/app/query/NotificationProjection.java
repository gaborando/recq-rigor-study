package com.study.app.query;

import com.study.app.domain.Events.ItemAddedEvent;
import com.study.app.domain.Events.ItemCheckedEvent;
import com.study.app.domain.Events.ItemUncheckedEvent;
import com.study.app.domain.Events.ListCreatedEvent;
import com.study.app.query.Queries.GetNotificationsQuery;
import com.study.app.query.Queries.NotificationResponse;
import com.study.app.query.Queries.NotificationsResponse;
import java.util.List;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.queryhandling.QueryHandler;
import org.springframework.stereotype.Component;

/**
 * Owns list-completion notifications. A single-threaded tracking processor
 * consumes the global event stream in order and, for each list, recomputes
 * whether every item is checked. The moment a list crosses ACTIVE->COMPLETED it
 * records exactly one notification — never zero, never two — even under a
 * concurrent final check, because the ordered stream serialises the decision
 * and the persisted {@link ListProgress} guards the transition.
 */
@Component
@ProcessingGroup("notifications")
public class NotificationProjection {

    private final ListProgressRepository progress;
    private final NotifItemStateRepository itemStates;
    private final NotificationRecordRepository notifications;

    public NotificationProjection(ListProgressRepository progress,
                                  NotifItemStateRepository itemStates,
                                  NotificationRecordRepository notifications) {
        this.progress = progress;
        this.itemStates = itemStates;
        this.notifications = notifications;
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void on(ListCreatedEvent e) {
        if (!progress.existsById(e.listId())) {
            progress.save(new ListProgress(e.listId(), false, 0));
        }
    }

    @EventHandler
    public void on(ItemAddedEvent e) {
        if (!itemStates.existsById(e.itemId())) {
            itemStates.save(new NotifItemState(e.itemId(), e.listId(), false));
        }
        evaluate(e.listId());
    }

    @EventHandler
    public void on(ItemCheckedEvent e) {
        itemStates.findById(e.itemId()).ifPresent(s -> {
            s.setChecked(true);
            itemStates.save(s);
        });
        evaluate(e.listId());
    }

    @EventHandler
    public void on(ItemUncheckedEvent e) {
        itemStates.findById(e.itemId()).ifPresent(s -> {
            s.setChecked(false);
            itemStates.save(s);
        });
        evaluate(e.listId());
    }

    /** Detect a transition and record exactly one notification per completion. */
    private void evaluate(String listId) {
        long total = itemStates.countByListId(listId);
        long checked = itemStates.countByListIdAndCheckedTrue(listId);
        boolean nowCompleted = total >= 1 && checked == total;

        ListProgress p = progress.findById(listId)
                .orElseGet(() -> new ListProgress(listId, false, 0));

        if (nowCompleted && !p.isCompleted()) {
            int seq = p.getCompletionCount() + 1;
            p.setCompleted(true);
            p.setCompletionCount(seq);
            progress.save(p);
            // Single-threaded processor: the existence check is sufficient; the
            // unique (listId, seq) constraint is the at-least-once safety net.
            if (!notifications.existsByListIdAndSeq(listId, seq)) {
                notifications.save(new NotificationRecord(listId, seq));
            }
        } else if (!nowCompleted && p.isCompleted()) {
            p.setCompleted(false);
            progress.save(p);
        } else {
            progress.save(p);
        }
    }

    // --------------------------------------------------------------- queries

    @QueryHandler
    public NotificationsResponse handle(GetNotificationsQuery q) {
        if (!progress.existsById(q.listId())) {
            return new NotificationsResponse(false, List.of());
        }
        List<NotificationResponse> result = notifications.findByListIdOrderBySeqAsc(q.listId()).stream()
                .map(n -> new NotificationResponse(n.getListId(), "COMPLETED"))
                .toList();
        return new NotificationsResponse(true, result);
    }
}
