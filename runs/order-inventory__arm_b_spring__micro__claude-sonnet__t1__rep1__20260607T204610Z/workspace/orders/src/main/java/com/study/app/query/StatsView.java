package com.study.app.query;

public record StatsView(long confirmed, long rejected, long revenue) {
    public StatsView(Long confirmed, Long rejected, Long revenue) {
        this(confirmed == null ? 0 : confirmed,
             rejected == null ? 0 : rejected,
             revenue == null ? 0 : revenue);
    }
}
