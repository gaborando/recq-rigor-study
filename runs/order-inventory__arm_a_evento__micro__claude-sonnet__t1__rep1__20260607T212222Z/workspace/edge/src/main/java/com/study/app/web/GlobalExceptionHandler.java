package com.study.app.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<?> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ExecutionException.class)
    public ResponseEntity<?> handleExecutionException(ExecutionException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof NoSuchElementException) {
            return ResponseEntity.status(404).body(Map.of("error", cause.getMessage()));
        }
        if (cause instanceof IllegalArgumentException) {
            return ResponseEntity.badRequest().body(Map.of("error", cause.getMessage()));
        }
        return ResponseEntity.status(500).body(Map.of("error", cause != null ? cause.getMessage() : ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
