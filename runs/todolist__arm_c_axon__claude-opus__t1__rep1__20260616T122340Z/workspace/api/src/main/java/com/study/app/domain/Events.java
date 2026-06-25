package com.study.app.domain;

/**
 * Shared domain events. Published by aggregates and distributed to every
 * subscribing projection / process across services via Axon Server. Each item
 * event carries its {@code listId} so consumers can correlate items to a list
 * without reading another service's database.
 */
public final class Events {

    private Events() {}

    public record ListCreatedEvent(String listId, String name) {}

    public record ItemAddedEvent(String itemId, String listId, String content) {}

    public record ItemCheckedEvent(String itemId, String listId) {}

    public record ItemUncheckedEvent(String itemId, String listId) {}

    public record ItemRenamedEvent(String itemId, String listId, String content) {}
}
