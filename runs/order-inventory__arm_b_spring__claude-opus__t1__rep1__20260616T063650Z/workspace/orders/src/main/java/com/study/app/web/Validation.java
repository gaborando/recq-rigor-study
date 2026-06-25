package com.study.app.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Tiny manual request validation (the web starter ships no bean-validation provider). */
final class Validation {
    private Validation() {}

    static void require(boolean ok, String message) {
        if (!ok) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    static String notBlank(String v, String field) {
        require(v != null && !v.isBlank(), field + " is required");
        return v;
    }

    static int atLeast(Integer v, int min, String field) {
        require(v != null && v >= min, field + " must be >= " + min);
        return v;
    }
}
