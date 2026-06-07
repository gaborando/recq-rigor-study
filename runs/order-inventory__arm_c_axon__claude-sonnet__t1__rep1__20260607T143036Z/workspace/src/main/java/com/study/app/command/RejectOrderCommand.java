package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RejectOrderCommand(@TargetAggregateIdentifier String orderId, String reason) {}
