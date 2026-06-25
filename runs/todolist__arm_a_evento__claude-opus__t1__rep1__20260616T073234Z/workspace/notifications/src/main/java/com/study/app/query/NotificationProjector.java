package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projector;
import com.evento.common.modeling.annotations.handler.EventHandler;
import com.study.app.domain.event.ItemCheckedEvent;
import com.study.app.domain.event.ListCreatedEvent;
import com.study.app.query.store.KnownListEntity;
import com.study.app.query.store.KnownListRepository;
import com.study.app.query.store.NotificationEntity;
import com.study.app.query.store.NotificationRepository;

/**
 * Records exactly one notification per ACTIVE -> COMPLETED transition. The aggregate
 * marks the single completing check with {@code completedTransition} and a per-list
 * {@code completionSeq}; the notification id is derived from that seq so the row is
 * written exactly once, never zero and never two, under any retry/redelivery.
 */
@Projector(version = 1)
public class NotificationProjector {

    private final KnownListRepository knownLists;
    private final NotificationRepository notifications;

    public NotificationProjector(KnownListRepository knownLists, NotificationRepository notifications) {
        this.knownLists = knownLists;
        this.notifications = notifications;
    }

    @EventHandler
    void on(ListCreatedEvent e) {
        if (!knownLists.existsById(e.getListId())) {
            knownLists.save(new KnownListEntity(e.getListId()));
        }
    }

    @EventHandler
    void on(ItemCheckedEvent e) {
        if (!e.isCompletedTransition()) return;
        String id = e.getListId() + ":" + e.getCompletionSeq();
        if (!notifications.existsById(id)) {
            notifications.save(new NotificationEntity(id, e.getListId(), e.getCompletionSeq(), "COMPLETED"));
        }
    }
}
