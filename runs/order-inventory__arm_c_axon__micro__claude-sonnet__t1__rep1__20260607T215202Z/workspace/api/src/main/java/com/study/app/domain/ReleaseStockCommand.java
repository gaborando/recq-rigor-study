package com.study.app.domain;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
public record ReleaseStockCommand(@TargetAggregateIdentifier String productId, String orderId, int quantity) {}
