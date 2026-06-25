package com.study.app.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maps errors that surface from the bus (wrapped in ExecutionException) to HTTP
 * codes by inspecting the cause chain:
 *   - not found / aggregate-not-initialized -> 404
 *   - bad argument / validation             -> 400
 *   - everything else                       -> 500
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
        return body(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        HttpStatus status = classify(ex);
        return body(status, rootMessage(ex));
    }

    private static HttpStatus classify(Throwable t) {
        Throwable c = t;
        while (c != null) {
            String name = c.getClass().getName();
            if (c instanceof NoSuchElementException) return HttpStatus.NOT_FOUND;
            if (name.contains("AggregateNotInitializedError")) return HttpStatus.NOT_FOUND;
            if (name.contains("NoSuchElementException")) return HttpStatus.NOT_FOUND;
            if (c instanceof IllegalArgumentException) return HttpStatus.BAD_REQUEST;
            if (name.contains("IllegalArgumentException")) return HttpStatus.BAD_REQUEST;
            String msg = c.getMessage();
            if (msg != null) {
                if (msg.contains("AggregateNotInitializedError") || msg.contains("NoSuchElementException")) {
                    return HttpStatus.NOT_FOUND;
                }
                if (msg.contains("IllegalArgumentException")) return HttpStatus.BAD_REQUEST;
            }
            if (c == c.getCause()) break;
            c = c.getCause();
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) c = c.getCause();
        return c.getMessage();
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", message == null ? status.getReasonPhrase() : message));
    }
}
