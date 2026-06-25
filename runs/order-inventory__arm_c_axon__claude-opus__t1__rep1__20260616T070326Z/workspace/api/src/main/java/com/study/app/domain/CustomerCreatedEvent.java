package com.study.app.domain;

public record CustomerCreatedEvent(String customerId, String name, int balance) {}
