package com.study.app.query;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * One row per booking decision. The UNIQUE(bookingId) makes the write
 * idempotent: a redelivered decision event (at-least-once under crash/retry)
 * cannot create a second notification.
 */
@Entity
@Table(name = "notification_view",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_booking", columnNames = "bookingId"))
public class NotificationRow {

    @Id
    private String bookingId;
    private String customerId;
    private String status;
    private String reason;

    protected NotificationRow() {
    }

    public NotificationRow(String bookingId, String customerId, String status, String reason) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.status = status;
        this.reason = reason;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
