package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Authoritative record of a list's identity and completion status. The status
 * is driven by completeness reports from the items service (applied in seq
 * order) — see {@code com.study.app.command.ListService}.
 */
@Entity
@Table(name = "todo_list")
public class TodoList {

    @Id
    private UUID id;

    private String name;

    /** ACTIVE or COMPLETED. */
    private String status;

    /** Highest completeness-report sequence applied (per-list, monotonic). */
    private long appliedSeq;

    /** Number of ACTIVE -> COMPLETED transitions; the notification dedupe key. */
    private long completionSeq;

    protected TodoList() {}

    public TodoList(UUID id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.appliedSeq = 0;
        this.completionSeq = 0;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getAppliedSeq() { return appliedSeq; }
    public void setAppliedSeq(long appliedSeq) { this.appliedSeq = appliedSeq; }
    public long getCompletionSeq() { return completionSeq; }
    public void setCompletionSeq(long completionSeq) { this.completionSeq = completionSeq; }
}
