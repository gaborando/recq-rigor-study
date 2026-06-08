package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class OrderView implements View {
    private String orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private String status;
    private String reason;
    private Integer total;

    public OrderView() {}

    public OrderView(String orderId, String customerId, String productId, int quantity,
                     String status, String reason, Integer total) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.reason = reason;
        this.total = total;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getTotal() { return total; }
    public void setTotal(Integer total) { this.total = total; }
}
