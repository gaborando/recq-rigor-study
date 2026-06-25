package com.study.app.query.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row per ACTIVE -> COMPLETED transition. The id is {@code listId:completionSeq},
 * a stable key derived from the aggregate's atomic decision, so re-delivery of the
 * triggering event can never create a duplicate notification.
 */
@Entity
@Table(name = "notification")
public class NotificationEntity {
    @Id
    private String id;
    private String listId;
    private long seq;
    private String status;

    public NotificationEntity() {}
    public NotificationEntity(String id, String listId, long seq, String status) {
        this.id = id;
        this.listId = listId;
        this.seq = seq;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getListId() { return listId; }
    public void setListId(String listId) { this.listId = listId; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
