package com.study.app.web;

import com.study.app.web.ApiExceptionHandler.BadRequest;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.TimeUnit;

/** Thin helpers over the query gateway with view-not-found -> 404 semantics. */
@Component
public class Gateways {

    private final QueryGateway queryGateway;

    public Gateways(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    public <Q, R> R queryOr404(Q query, Class<R> type) {
        R result = querySingle(query, type);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return result;
    }

    public <Q, R> R querySingle(Q query, Class<R> type) {
        try {
            return queryGateway.query(query, ResponseTypes.instanceOf(type)).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "query failed", e);
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new BadRequest(message);
        }
    }
}
