package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One row per ACTIVE->COMPLETED transition of a list. The unique
 * {@code (listId, seq)} constraint is the exactly-once safety net: a redelivered
 * event can never create a second row for the same transition.
 */
@Entity
@Table(name = "notification_record",
        uniqueConstraints = @UniqueConstraint(name = "uq_notif_list_seq", columnNames = {"listId", "seq"}))
public class NotificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String listId;
    private int seq;

    protected NotificationRecord() {
    }

    public NotificationRecord(String listId, int seq) {
        this.listId = listId;
        this.seq = seq;
    }

    public String getListId() {
        return listId;
    }

    public int getSeq() {
        return seq;
    }
}
