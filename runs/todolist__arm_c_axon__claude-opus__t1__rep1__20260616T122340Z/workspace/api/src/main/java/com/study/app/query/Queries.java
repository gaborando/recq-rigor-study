package com.study.app.query;

import java.util.List;

/**
 * Shared query messages and their response DTOs. Query handlers live in the
 * owning service (lists / notifications); the edge dispatches via the
 * QueryGateway and serialises the responses straight to HTTP JSON.
 */
public final class Queries {

    private Queries() {}

    // ----- queries -----
    public record GetListViewQuery(String listId) {}

    public record GetNotificationsQuery(String listId) {}

    public record GetStatsQuery() {}

    // ----- response DTOs (field names are the HTTP JSON contract) -----
    public record ItemResponse(String itemId, String content, boolean checked) {}

    public record ListViewResponse(String listId, String name, String status, List<ItemResponse> items) {}

    public record ListSummaryResponse(String listId, String name, String status) {}

    public record NotificationResponse(String listId, String status) {}

    /** found=false means the list is unknown -> the edge maps it to HTTP 404. */
    public record NotificationsResponse(boolean found, List<NotificationResponse> notifications) {}

    public record StatsResponse(long active, long completed, long totalItems, long checkedItems) {}
}
