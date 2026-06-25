package com.study.app.query;

/** The portion of /stats/lists owned by the items service. */
public record ItemStatsView(long totalItems, long checkedItems) {}
