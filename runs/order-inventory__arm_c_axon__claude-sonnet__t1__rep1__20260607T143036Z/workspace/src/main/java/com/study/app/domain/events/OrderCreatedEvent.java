package com.study.app.domain.events;

public record OrderCreatedEvent(String orderId, String customerId, String productId, int quantity) {}
