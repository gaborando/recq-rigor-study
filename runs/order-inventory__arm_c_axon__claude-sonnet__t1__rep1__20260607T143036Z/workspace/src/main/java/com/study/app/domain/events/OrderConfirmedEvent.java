package com.study.app.domain.events;

public record OrderConfirmedEvent(String orderId, int total) {}
