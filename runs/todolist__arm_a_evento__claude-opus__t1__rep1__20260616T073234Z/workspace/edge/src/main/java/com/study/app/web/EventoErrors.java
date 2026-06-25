package com.study.app.web;

/**
 * Classifies failures that come back from the bus. Handler exceptions cross the
 * Evento server reconstructed by class name (falling back to RuntimeException with
 * the original message), so we match on both the concrete type chain and the message
 * marker to stay robust regardless of how the exception was rehydrated.
 */
final class EventoErrors {
    private EventoErrors() {}

    static boolean isNotFound(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String cls = c.getClass().getName();
            String msg = c.getMessage() == null ? "" : c.getMessage();
            if (cls.endsWith("NotFoundException")
                    || cls.endsWith("AggregateNotInitializedError")
                    || cls.endsWith("AggregateDeletedError")
                    || msg.contains("NOT_FOUND")
                    || msg.contains("not been initialized")
                    || msg.contains("AggregateNotInitialized")) {
                return true;
            }
            if (c.getCause() == c) break;
        }
        return false;
    }

    static boolean isAlreadyInitialized(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String cls = c.getClass().getName();
            String msg = c.getMessage() == null ? "" : c.getMessage();
            if (cls.endsWith("AggregateInitializedError")
                    || msg.contains("AggregateInitialized")
                    || msg.contains("already been initialized")) {
                return true;
            }
            if (c.getCause() == c) break;
        }
        return false;
    }
}
