package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

/**
 * Shared command messages routed across services through Axon Server.
 * List commands target the {@code TodoListAggregate} (lists service);
 * item commands target the {@code ItemAggregate} (items service).
 */
public final class Commands {

    private Commands() {}

    // ----- list (lists service) -----
    public record CreateListCommand(@TargetAggregateIdentifier String listId, String name) {}

    // ----- item (items service) -----
    public record AddItemCommand(@TargetAggregateIdentifier String itemId, String listId, String content) {}

    public record CheckItemCommand(@TargetAggregateIdentifier String itemId) {}

    public record UncheckItemCommand(@TargetAggregateIdentifier String itemId) {}

    public record RenameItemCommand(@TargetAggregateIdentifier String itemId, String content) {}
}
