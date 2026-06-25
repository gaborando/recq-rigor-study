package com.study.app.domain.error;

/**
 * Thrown by command/query handlers when a referenced list or item does not exist.
 * Surfaces to the caller across the bus (reconstructed via its String constructor)
 * and is mapped to HTTP 404 by the edge controller.
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) { super(message); }
}
