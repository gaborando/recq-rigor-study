package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NotificationView implements View {
    private String orderId;
    private String status;
    private String reason;

    public NotificationView() {}
    public NotificationView(String orderId, String status, String reason) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
