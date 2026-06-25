package com.study.app.query;

/** The portion of /stats/lists owned by the lists service. */
public record StatsView(long active, long completed) {}
