package com.study.app.domain.events;

public record CustomerCreatedEvent(String customerId, String name, int balance) {}
