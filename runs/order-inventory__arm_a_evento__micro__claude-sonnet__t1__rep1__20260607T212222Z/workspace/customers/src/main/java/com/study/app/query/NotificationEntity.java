package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    private String orderId;
    private String customerId;
    private String status;
    private String reason;

    public NotificationEntity() {}

    public NotificationEntity(String orderId, String customerId, String status, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
