package com.study.app.domain.events;

public record StockReservedEvent(String productId, String orderId, String customerId, int quantity, int total) {}
