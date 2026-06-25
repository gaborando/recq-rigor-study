package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record CreateCustomerCommand(@TargetAggregateIdentifier String customerId, String name, int balance) {}
