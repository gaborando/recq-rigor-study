package com.study.app.command;

import org.axonframework.modelling.command.TargetAggregateIdentifier;

public record CreateProductCommand(@TargetAggregateIdentifier String productId, String name, int unitPrice, int stock) {}
