package com.study.app.domain;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
public record RestockProductCommand(@TargetAggregateIdentifier String productId, int units) {}
