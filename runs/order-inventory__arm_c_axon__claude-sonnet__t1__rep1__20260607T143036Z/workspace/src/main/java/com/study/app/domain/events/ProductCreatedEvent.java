package com.study.app.domain.events;

public record ProductCreatedEvent(String productId, String name, int unitPrice, int stock) {}
