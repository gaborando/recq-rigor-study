package com.study.app.domain;
public record CreateOrderCommand(String orderId, String customerId, String productId, int quantity) {}
