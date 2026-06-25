package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** One row per booking decision. bookingId is the primary key, so a duplicate
 *  decision event can never create a second notification (exactly once). */
@Entity
@Table(name = "notification", indexes = @Index(name = "idx_notif_customer", columnList = "customerId"))
public class NotificationEntity {
    @Id
    private String bookingId;
    private String customerId;
    private String status;
    private String reason;

    public NotificationEntity() {}
    public NotificationEntity(String bookingId, String customerId, String status, String reason) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
