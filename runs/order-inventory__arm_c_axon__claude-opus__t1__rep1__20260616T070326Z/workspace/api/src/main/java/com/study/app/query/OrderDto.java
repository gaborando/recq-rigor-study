package com.study.app.query;

public record OrderDto(String orderId, String customerId, String productId, int quantity,
                       String status, String reason, Integer total) {}
