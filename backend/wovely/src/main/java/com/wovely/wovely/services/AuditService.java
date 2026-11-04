package com.wovely.wovely.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.wovely.wovely.models.AuditLog;
import com.wovely.wovely.repository.AuditLogRepository;
import com.wovely.wovely.security.services.UserDetailsImpl;

/**
 * Single entry point for the admin audit trail (Story 1: "all manual order changes must be logged").
 * Failures are logged, never thrown, so auditing can't break the business action.
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /** Records an action by the authenticated caller (or "system" when there is none). */
    public void record(String actionType, String targetId, String details) {
        String actorId = "system";
        String actorName = "system";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl me) {
            actorId = me.getId();
            actorName = me.getUsername();
        }
        record(actorId, actorName, actionType, targetId, details);
    }

    /** Records an action on behalf of an explicit actor (used for events reported by other services). */
    public void record(String actorId, String actorName, String actionType, String targetId, String details) {
        try {
            auditLogRepository.save(new AuditLog(actorId, actorName, actionType, targetId, details));
        } catch (RuntimeException e) {
            logger.error("Audit logging failed for {} on {}: {}", actionType, targetId, e.getMessage());
        }
    }
}
