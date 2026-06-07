package com.study.app.domain.events;

public record FundsDepositedEvent(String customerId, int amount) {}
