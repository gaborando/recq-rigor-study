package com.study.app.query;

import com.evento.common.modeling.annotations.component.Projection;
import com.evento.common.modeling.annotations.handler.QueryHandler;
import com.evento.common.modeling.messaging.query.Multiple;
import com.study.app.domain.error.NotFoundException;
import com.study.app.domain.query.GetNotificationsQuery;
import com.study.app.domain.view.NotificationView;
import com.study.app.query.store.KnownListRepository;
import com.study.app.query.store.NotificationRepository;

import java.util.List;
import java.util.stream.Collectors;

@Projection
public class NotificationProjection {

    private final KnownListRepository knownLists;
    private final NotificationRepository notifications;

    public NotificationProjection(KnownListRepository knownLists, NotificationRepository notifications) {
        this.knownLists = knownLists;
        this.notifications = notifications;
    }

    @QueryHandler
    Multiple<NotificationView> handle(GetNotificationsQuery q) {
        if (!knownLists.existsById(q.getListId())) {
            throw new NotFoundException("NOT_FOUND list " + q.getListId());
        }
        List<NotificationView> views = notifications.findByListIdOrderBySeqAsc(q.getListId()).stream()
                .map(n -> new NotificationView(n.getListId(), n.getStatus()))
                .collect(Collectors.toList());
        return Multiple.of(views);
    }
}
