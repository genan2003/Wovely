package com.wovely.wovely.payload.request;

import com.wovely.wovely.models.EAccountStatus;

public class PenaltyRequest {
    private String action; // "STRIKE", "STATUS"
    private EAccountStatus newStatus;
    private Integer suspendDays;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public EAccountStatus getNewStatus() { return newStatus; }
    public void setNewStatus(EAccountStatus newStatus) { this.newStatus = newStatus; }

    public Integer getSuspendDays() { return suspendDays; }
    public void setSuspendDays(Integer suspendDays) { this.suspendDays = suspendDays; }
}
