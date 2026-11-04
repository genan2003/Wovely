package com.wovely.wovely.models;

/** Moderation outcome of a report. Only PENDING reports can be decided (PLAN.md 3.2). */
public enum EReportStatus {
    PENDING,
    RESOLVED,
    DISMISSED
}
