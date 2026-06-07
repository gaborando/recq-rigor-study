package com.study.app.domain.events;

public record ProductRestockedEvent(String productId, int units) {}
