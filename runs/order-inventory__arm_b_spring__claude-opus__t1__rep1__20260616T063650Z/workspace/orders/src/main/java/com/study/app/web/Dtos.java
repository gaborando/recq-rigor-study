package com.study.app.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.study.app.domain.OrderEntity;

/** Request/response payloads for the orders HTTP API. */
public final class Dtos {
    private Dtos() {}

    public record CreateOrder(String orderId, String customerId, String productId, Integer quantity) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderView(String orderId, String customerId, String productId, int quantity,
                            String status, String reason, Long total) {

        public static OrderView of(OrderEntity o) {
            return new OrderView(
                    o.getOrderId(), o.getCustomerId(), o.getProductId(), o.getQuantity(),
                    o.getStatus().name(),
                    o.getStatus() == OrderEntity.Status.REJECTED ? o.getReason() : null,
                    o.getStatus() == OrderEntity.Status.CONFIRMED ? o.getTotal() : null);
        }
    }

    public record StatsView(long confirmed, long rejected, long revenue) {}
}
