package com.study.app.command;

public record CreateCustomerCommand(String customerId, String name, int balance) {}
