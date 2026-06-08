package com.study.app.domain;
public record OrderCreatedEvent(String orderId, String customerId, String productId, int quantity) {}
