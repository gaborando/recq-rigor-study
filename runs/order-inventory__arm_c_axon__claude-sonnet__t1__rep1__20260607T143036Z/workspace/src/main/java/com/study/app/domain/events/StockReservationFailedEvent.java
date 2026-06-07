package com.study.app.domain.events;

public record StockReservationFailedEvent(String productId, String orderId, String reason) {}
