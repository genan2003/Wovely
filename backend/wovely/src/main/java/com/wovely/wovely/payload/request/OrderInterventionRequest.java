package com.wovely.wovely.payload.request;

import com.wovely.wovely.models.EOrderStatus;

public class OrderInterventionRequest {
    private String action;
    private EOrderStatus newStatus;
    private String reason;
    private String adminNotes;
    private String trackingNumber;

    public OrderInterventionRequest() {
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public EOrderStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(EOrderStatus newStatus) {
        this.newStatus = newStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getAdminNotes() {
        return adminNotes;
    }

    public void setAdminNotes(String adminNotes) {
        this.adminNotes = adminNotes;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }
}
