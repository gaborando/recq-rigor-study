package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ReserveStockCommand(@TargetAggregateIdentifier String productId, String orderId, int quantity) {}
