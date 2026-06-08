package com.study.app.domain;
public record ChargeFailedEvent(String customerId, String orderId, String reason) {}
