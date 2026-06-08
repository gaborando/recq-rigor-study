package com.study.app.domain;
public record OrderConfirmedEvent(String orderId, String customerId, int total) {}
