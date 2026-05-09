package com.wovely.wovely.models;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Document(collection = "reports")
public class Report {
    @Id
    private String id;
    private String reporterId;
    private String reporterName;
    private String targetType; // PRODUCT or CHAT
    private String targetId;
    private String targetName;
    private String reason;
    private String status; // PENDING, RESOLVED, DISMISSED
    private LocalDateTime createdAt;

    public Report() {
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    public Report(String reporterId, String targetType, String targetId, String reason) {
        this();
        this.reporterId = reporterId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getReporterId() { return reporterId; }
    public void setReporterId(String reporterId) { this.reporterId = reporterId; }
    public String getReporterName() { return reporterName; }
    public void setReporterName(String reporterName) { this.reporterName = reporterName; }
    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }
    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
