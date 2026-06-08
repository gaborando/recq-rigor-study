package com.study.app.domain;
public record OrderRejectedEvent(String orderId, String customerId, String reason) {}
