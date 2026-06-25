package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record CreateOrderCommand(@TargetAggregateIdentifier String orderId, String customerId, String productId, int quantity) {}
