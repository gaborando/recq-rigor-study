package com.study.app.domain;

import java.util.ArrayList;
import java.util.List;

/** Deterministic seat-id generation shared by every bundle so the labels
 *  reported at creation match the labels in the read model ("1A".."30F"). */
public final class SeatIds {
    private static final char[] COLS = {'A', 'B', 'C', 'D', 'E', 'F'};

    private SeatIds() {}

    public static List<String> generate(int seatCount) {
        List<String> seats = new ArrayList<>(Math.max(seatCount, 0));
        for (int i = 0; i < seatCount; i++) {
            int row = i / COLS.length + 1;
            char col = COLS[i % COLS.length];
            seats.add(row + String.valueOf(col));
        }
        return seats;
    }
}
