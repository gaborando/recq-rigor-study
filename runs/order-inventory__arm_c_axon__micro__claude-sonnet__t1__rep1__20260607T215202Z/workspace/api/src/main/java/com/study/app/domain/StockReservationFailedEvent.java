package com.study.app.domain;
public record StockReservationFailedEvent(String productId, String orderId, String reason) {}
