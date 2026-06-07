package com.study.app.command;

public record CreateOrderCommand(String orderId, String customerId, String productId, int quantity) {}
