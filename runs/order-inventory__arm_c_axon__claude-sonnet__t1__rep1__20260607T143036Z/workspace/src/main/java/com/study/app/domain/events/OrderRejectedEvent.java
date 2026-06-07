package com.study.app.domain.events;

public record OrderRejectedEvent(String orderId, String reason) {}
