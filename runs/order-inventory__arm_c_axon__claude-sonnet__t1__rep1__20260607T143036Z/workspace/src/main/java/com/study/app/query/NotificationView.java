package com.study.app.query;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_view",
        uniqueConstraints = @UniqueConstraint(columnNames = "orderId"))
public class NotificationView {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String orderId;
    private String customerId;
    private String status;
    private String reason;

    protected NotificationView() {}

    public NotificationView(String orderId, String customerId, String status, String reason) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public String getOrderId() { return orderId; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
