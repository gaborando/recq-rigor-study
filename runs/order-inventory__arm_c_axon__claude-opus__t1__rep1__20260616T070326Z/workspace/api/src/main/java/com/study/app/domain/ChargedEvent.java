package com.study.app.domain;

public record ChargedEvent(String customerId, String orderId, int total) {}
