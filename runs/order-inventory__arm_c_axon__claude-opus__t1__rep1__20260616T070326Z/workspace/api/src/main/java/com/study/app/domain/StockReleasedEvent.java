package com.study.app.domain;

public record StockReleasedEvent(String productId, String orderId, int quantity) {}
