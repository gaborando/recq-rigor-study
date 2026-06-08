package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class OrderStatsView implements View {
    private long confirmed;
    private long rejected;
    private long revenue;

    public OrderStatsView() {}

    public OrderStatsView(long confirmed, long rejected, long revenue) {
        this.confirmed = confirmed;
        this.rejected = rejected;
        this.revenue = revenue;
    }

    public long getConfirmed() { return confirmed; }
    public void setConfirmed(long confirmed) { this.confirmed = confirmed; }
    public long getRejected() { return rejected; }
    public void setRejected(long rejected) { this.rejected = rejected; }
    public long getRevenue() { return revenue; }
    public void setRevenue(long revenue) { this.revenue = revenue; }
}
