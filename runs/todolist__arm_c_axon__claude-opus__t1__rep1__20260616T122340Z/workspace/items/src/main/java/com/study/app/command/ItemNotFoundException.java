package com.study.app.command;

/**
 * Thrown when a check/uncheck/rename command targets an item that was never
 * added. The message carries the {@code ITEM_NOT_FOUND} marker so the edge can
 * recognise it after the failure is relayed back across Axon Server and map it
 * to HTTP 404.
 */
public class ItemNotFoundException extends RuntimeException {

    public static final String MARKER = "ITEM_NOT_FOUND";

    public ItemNotFoundException(String itemId) {
        super(MARKER + ": " + itemId);
    }
}
