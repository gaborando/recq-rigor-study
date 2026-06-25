package com.study.app.command;

import com.study.app.domain.ListRef;
import com.study.app.domain.Notification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final String COMPLETED = "COMPLETED";

    private final NotificationRepository notifications;
    private final ListRefRepository listRefs;

    public NotificationService(NotificationRepository notifications, ListRefRepository listRefs) {
        this.notifications = notifications;
        this.listRefs = listRefs;
    }

    @Transactional
    public void register(UUID listId) {
        if (listRefs.existsById(listId)) return;
        try {
            listRefs.saveAndFlush(new ListRef(listId));
        } catch (DataIntegrityViolationException dup) {
            // concurrent register — fine
        }
    }

    /** Record a completion transition; idempotent via UNIQUE(listId, transitionSeq). */
    @Transactional
    public void record(UUID listId, long transitionSeq) {
        try {
            notifications.saveAndFlush(new Notification(listId, transitionSeq, COMPLETED));
        } catch (DataIntegrityViolationException duplicate) {
            // exactly-once: redelivery of the same transition is a no-op
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> forList(UUID listId) {
        if (!listRefs.existsById(listId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown list");
        }
        return notifications.findByListIdOrderByTransitionSeqAsc(listId);
    }
}
