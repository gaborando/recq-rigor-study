package com.study.app.domain;
public record CreateCustomerCommand(String customerId, String name, int balance) {}
