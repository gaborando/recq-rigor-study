package com.study.app.domain.view;

import com.evento.common.modeling.messaging.payload.View;

public class NotificationView implements View {
    private String bookingId;
    private String status;
    private String reason;

    public NotificationView() {}
    public NotificationView(String bookingId, String status, String reason) {
        this.bookingId = bookingId;
        this.status = status;
        this.reason = reason;
    }

    public String getBookingId() { return bookingId; }
    public void setBookingId(String bookingId) { this.bookingId = bookingId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
