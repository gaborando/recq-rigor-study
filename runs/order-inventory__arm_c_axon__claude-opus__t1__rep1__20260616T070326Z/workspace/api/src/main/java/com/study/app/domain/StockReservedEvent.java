package com.study.app.domain;

public record StockReservedEvent(String productId, String orderId, int quantity, int unitPrice, int total) {}
