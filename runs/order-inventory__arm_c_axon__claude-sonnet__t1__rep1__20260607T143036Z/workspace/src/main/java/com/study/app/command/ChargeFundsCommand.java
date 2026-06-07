package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ChargeFundsCommand(
        @TargetAggregateIdentifier String customerId,
        String orderId,
        String productId,
        int quantity,
        int amount) {}
