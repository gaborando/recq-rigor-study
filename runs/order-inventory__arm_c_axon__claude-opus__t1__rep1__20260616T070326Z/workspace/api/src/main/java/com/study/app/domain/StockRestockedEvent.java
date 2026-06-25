package com.study.app.domain;

public record StockRestockedEvent(String productId, int units) {}
