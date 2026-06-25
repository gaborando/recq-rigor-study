package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ChargeCommand(@TargetAggregateIdentifier String customerId, String orderId, int total) {}
