package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record DepositFundsCommand(@TargetAggregateIdentifier String customerId, int amount) {}
