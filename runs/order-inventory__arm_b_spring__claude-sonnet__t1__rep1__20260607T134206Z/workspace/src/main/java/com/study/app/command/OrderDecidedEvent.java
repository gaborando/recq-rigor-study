package com.study.app.command;

import com.study.app.domain.OrderStatus;
import java.util.UUID;

public record OrderDecidedEvent(UUID orderId, UUID customerId, OrderStatus status, String reason) {}
