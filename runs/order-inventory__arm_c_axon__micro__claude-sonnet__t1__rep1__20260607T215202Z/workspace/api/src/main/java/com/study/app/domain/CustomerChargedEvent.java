package com.study.app.domain;
public record CustomerChargedEvent(String customerId, String orderId, int amount) {}
