package com.study.app.domain.events;

public record StockReleasedEvent(String productId, String orderId, int quantity) {}
