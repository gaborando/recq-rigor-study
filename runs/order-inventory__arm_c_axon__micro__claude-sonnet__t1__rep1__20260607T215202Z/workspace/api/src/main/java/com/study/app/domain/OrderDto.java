package com.study.app.domain;
public record OrderDto(String orderId, String customerId, String productId, int quantity, String status, String reason, Integer total) {}
