package com.study.app.domain;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
public record ConfirmOrderCommand(@TargetAggregateIdentifier String orderId, int total) {}
