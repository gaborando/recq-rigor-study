package com.study.app.query;

import java.util.concurrent.atomic.AtomicLong;

public final class OrderStatsStore {
    private static final AtomicLong confirmed = new AtomicLong(0);
    private static final AtomicLong rejected = new AtomicLong(0);
    private static final AtomicLong revenue = new AtomicLong(0);

    private OrderStatsStore() {}

    public static void incrementConfirmed() { confirmed.incrementAndGet(); }
    public static void addRevenue(long amount) { revenue.addAndGet(amount); }
    public static void incrementRejected() { rejected.incrementAndGet(); }

    public static long getConfirmed() { return confirmed.get(); }
    public static long getRejected() { return rejected.get(); }
    public static long getRevenue() { return revenue.get(); }
}
