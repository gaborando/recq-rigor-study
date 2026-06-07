package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RestockProductCommand(@TargetAggregateIdentifier String productId, int units) {}
