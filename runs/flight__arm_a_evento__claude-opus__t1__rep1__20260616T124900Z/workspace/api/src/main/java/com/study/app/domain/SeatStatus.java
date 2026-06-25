package com.study.app.domain;

/** Shared seat lifecycle states (used by the write and read sides of flights). */
public final class SeatStatus {
    public static final String AVAILABLE = "AVAILABLE";
    public static final String HELD = "HELD";
    public static final String BOOKED = "BOOKED";

    private SeatStatus() {}
}
