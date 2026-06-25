package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record DepositCommand(@TargetAggregateIdentifier String customerId, int amount) {}
