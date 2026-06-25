package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * One row per ACTIVE -> COMPLETED transition. The UNIQUE(listId, transitionSeq)
 * constraint makes redelivery from the lists outbox a no-op: exactly one
 * notification per transition.
 */
@Entity
@Table(name = "notification",
       uniqueConstraints = @UniqueConstraint(columnNames = {"listId", "transitionSeq"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID listId;

    private long transitionSeq;

    private String status;

    protected Notification() {}

    public Notification(UUID listId, long transitionSeq, String status) {
        this.listId = listId;
        this.transitionSeq = transitionSeq;
        this.status = status;
    }

    public UUID getListId() { return listId; }
    public long getTransitionSeq() { return transitionSeq; }
    public String getStatus() { return status; }
}
