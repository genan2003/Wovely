package com.wovely.wovely.controllers;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.wovely.common.error.ApiException;
import com.wovely.wovely.services.AuditService;

/**
 * Lets the products service write admin moderation actions into the shared audit trail
 * (PLAN.md 2.7). Internal API key only.
 */
@RestController
@RequestMapping("/internal/audit")
public class InternalAuditController {

    private final AuditService auditService;

    public InternalAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void record(@RequestBody Map<String, String> event) {
        String actionType = event.get("actionType");
        if (actionType == null || actionType.isBlank()) {
            throw ApiException.badRequest("actionType is required");
        }
        auditService.record(event.getOrDefault("actorId", "unknown"), event.getOrDefault("actorUsername", "unknown"),
                actionType, event.get("targetId"), event.get("details"));
    }
}
