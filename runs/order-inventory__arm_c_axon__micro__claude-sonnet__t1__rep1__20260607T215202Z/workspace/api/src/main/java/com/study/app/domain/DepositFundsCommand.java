package com.study.app.domain;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
public record DepositFundsCommand(@TargetAggregateIdentifier String customerId, int amount) {}
