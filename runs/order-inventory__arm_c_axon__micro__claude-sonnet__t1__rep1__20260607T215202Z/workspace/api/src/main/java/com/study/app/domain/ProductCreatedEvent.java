package com.study.app.domain;
public record ProductCreatedEvent(String productId, String name, int unitPrice, int stock) {}
