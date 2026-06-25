package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ReleaseStockCommand(@TargetAggregateIdentifier String productId, String orderId, int quantity) {}
