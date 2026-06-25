package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Outbox of completeness reports for the lists service, written in the same
 * transaction as the item mutation that produced the edge. A dispatcher
 * delivers them at-least-once; the lists service applies them idempotently by
 * per-list seq.
 */
@Entity
@Table(name = "completion_outbox")
public class CompletionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID listId;

    private long seq;

    private boolean complete;

    private boolean sent;

    protected CompletionOutbox() {}

    public CompletionOutbox(UUID listId, long seq, boolean complete) {
        this.listId = listId;
        this.seq = seq;
        this.complete = complete;
        this.sent = false;
    }

    public Long getId() { return id; }
    public UUID getListId() { return listId; }
    public long getSeq() { return seq; }
    public boolean isComplete() { return complete; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
}
