package com.study.app.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One notification per booking decision. bookingId is the primary key, so even
 * under retries or recovery a booking can produce at most one notification.
 */
@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    private String bookingId;
    private String customerId;
    private String status;
    private String reason;

    protected Notification() {}

    public Notification(String bookingId, String customerId, String status, String reason) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public String getBookingId() { return bookingId; }
    public String getCustomerId() { return customerId; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
}
