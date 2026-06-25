package com.study.app.api.support;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable seat-label generation shared by the edge (for the create response) and
 * the flights service (for the create event), so the ids never drift.
 *
 * Seats are laid out in rows of six columns A..F: seat 0 -> "1A", seat 6 -> "2A".
 */
public final class Seats {

    private static final char[] COLS = {'A', 'B', 'C', 'D', 'E', 'F'};

    private Seats() {
    }

    public static List<String> labels(int seatCount) {
        List<String> labels = new ArrayList<>(seatCount);
        for (int i = 0; i < seatCount; i++) {
            int row = i / COLS.length + 1;
            char col = COLS[i % COLS.length];
            labels.add(row + String.valueOf(col));
        }
        return labels;
    }
}
