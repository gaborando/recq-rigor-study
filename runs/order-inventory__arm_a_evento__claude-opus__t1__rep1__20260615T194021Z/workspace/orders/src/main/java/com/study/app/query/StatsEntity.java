package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_stats")
public class StatsEntity {
    @Id
    private String id;
    private long confirmed;
    private long rejected;
    private long revenue;

    public StatsEntity() {}
    public StatsEntity(String id, long confirmed, long rejected, long revenue) {
        this.id = id;
        this.confirmed = confirmed;
        this.rejected = rejected;
        this.revenue = revenue;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getConfirmed() { return confirmed; }
    public void setConfirmed(long confirmed) { this.confirmed = confirmed; }
    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }
    public long getRevenue() { return revenue; }
    public void setRevenue(long revenue) { this.revenue = revenue; }
}
