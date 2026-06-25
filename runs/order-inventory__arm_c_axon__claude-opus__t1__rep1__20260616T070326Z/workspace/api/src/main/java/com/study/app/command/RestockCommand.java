package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record RestockCommand(@TargetAggregateIdentifier String productId, int units) {}
