package com.study.app.domain;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
public record ChargeCustomerCommand(@TargetAggregateIdentifier String customerId, String orderId, int amount) {}
