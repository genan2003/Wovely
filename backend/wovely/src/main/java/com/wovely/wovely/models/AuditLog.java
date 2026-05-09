package com.wovely.wovely.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "audit_logs")
public class AuditLog {
    @Id
    private String id;
    private String adminId;
    private String adminUsername;
    private String actionType; // ORDER_OVERRIDE, USER_SUSPEND, etc.
    private String targetId;
    private String details;
    private LocalDateTime timestamp;

    public AuditLog() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLog(String adminId, String adminUsername, String actionType, String targetId, String details) {
        this();
        this.adminId = adminId;
        this.adminUsername = adminUsername;
        this.actionType = actionType;
        this.targetId = targetId;
        this.details = details;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getAdminId() { return adminId; }
    public void setAdminId(String adminId) { this.adminId = adminId; }
    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
