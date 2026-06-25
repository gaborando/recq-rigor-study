package com.study.app.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Malformed JSON body / wrong types -> 400. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> badBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "malformed body"));
    }

    @ExceptionHandler(BadRequest.class)
    public ResponseEntity<Map<String, String>> badRequest(BadRequest e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /** Re-thrown 404s from controllers. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Void> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).build();
    }

    /** A validation error surfaced from a command handler. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> illegal(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", String.valueOf(e.getMessage())));
    }

    /** Signals a 400 from controller-side validation. */
    public static class BadRequest extends RuntimeException {
        public BadRequest(String message) {
            super(message);
        }
    }
}
