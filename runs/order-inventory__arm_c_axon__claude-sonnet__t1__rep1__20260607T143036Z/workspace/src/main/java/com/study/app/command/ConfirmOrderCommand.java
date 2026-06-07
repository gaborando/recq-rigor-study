package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record ConfirmOrderCommand(@TargetAggregateIdentifier String orderId, int total) {}
