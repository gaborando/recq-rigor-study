package com.study.app.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A charge attempt, keyed by bookingId. Its existence makes charging idempotent:
 * the same bookingId never decrements a balance twice.
 */
@Entity
@Table(name = "charges")
public class Charge {
    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Id
    private String bookingId;
    private String customerId;
    private long amount;
    private String status;
    private boolean refunded;

    protected Charge() {}

    public Charge(String bookingId, String customerId, long amount, String status) {
        this.bookingId = bookingId;
        this.customerId = customerId;
        this.amount = amount;
        this.status = status;
    }

    public String getBookingId() { return bookingId; }
    public String getCustomerId() { return customerId; }
    public long getAmount() { return amount; }
    public String getStatus() { return status; }
    public boolean isRefunded() { return refunded; }
    public void setRefunded(boolean refunded) { this.refunded = refunded; }
}
