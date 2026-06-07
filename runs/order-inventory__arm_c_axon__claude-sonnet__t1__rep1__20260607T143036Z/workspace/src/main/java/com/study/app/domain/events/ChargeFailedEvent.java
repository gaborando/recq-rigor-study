package com.study.app.domain.events;

public record ChargeFailedEvent(String customerId, String orderId, String productId, int quantity) {}
