package com.study.app.domain;

import jakarta.persistence.*;
import java.util.UUID;

/**
 * Local registry of lists known to this service. Doubles as the per-list lock
 * target that serializes item mutations + completeness computation for a list,
 * and remembers the last completeness value reported to the lists service so we
 * only emit on a change (edge).
 */
@Entity
@Table(name = "list_ref")
public class ListRef {

    @Id
    private UUID listId;

    private boolean lastReportedComplete;

    private long reportSeq;

    protected ListRef() {}

    public ListRef(UUID listId) {
        this.listId = listId;
        this.lastReportedComplete = false;
        this.reportSeq = 0;
    }

    public UUID getListId() { return listId; }
    public boolean isLastReportedComplete() { return lastReportedComplete; }
    public void setLastReportedComplete(boolean v) { this.lastReportedComplete = v; }
    public long getReportSeq() { return reportSeq; }
    public void setReportSeq(long reportSeq) { this.reportSeq = reportSeq; }
}
