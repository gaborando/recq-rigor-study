package com.study.app.command;

import com.study.app.domain.Notification;
import com.study.app.domain.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NotificationService {

    @Autowired
    private NotificationRepository notificationRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderDecided(OrderDecidedEvent event) {
        try {
            notificationRepository.saveAndFlush(
                new Notification(event.orderId(), event.customerId(), event.status(), event.reason())
            );
        } catch (DataIntegrityViolationException e) {
            // already notified (idempotent)
        }
    }
}
