package com.study.app.query;

import java.util.UUID;

/** Public list summary: identity + current completion status. */
public record ListSummary(UUID listId, String name, String status) {}
