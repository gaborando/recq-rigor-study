package com.study.app.domain.events;

public record FundsChargedEvent(String customerId, String orderId, int amount) {}
