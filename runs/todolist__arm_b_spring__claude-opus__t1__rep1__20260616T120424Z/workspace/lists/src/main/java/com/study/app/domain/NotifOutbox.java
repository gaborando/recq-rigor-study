package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Transactional outbox row written in the same transaction as an
 * ACTIVE -> COMPLETED transition. A background dispatcher delivers it to the
 * notifications service at-least-once; the notifications service dedupes by
 * (listId, transitionSeq) so the net effect is exactly-once.
 */
@Entity
@Table(name = "notif_outbox")
public class NotifOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID listId;

    private long transitionSeq;

    private boolean sent;

    protected NotifOutbox() {}

    public NotifOutbox(UUID listId, long transitionSeq) {
        this.listId = listId;
        this.transitionSeq = transitionSeq;
        this.sent = false;
    }

    public Long getId() { return id; }
    public UUID getListId() { return listId; }
    public long getTransitionSeq() { return transitionSeq; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}
