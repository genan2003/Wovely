package com.wovely.products.clients;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.wovely.common.security.JwtPrincipal;
import com.wovely.common.security.OwnershipGuard;

/**
 * Sends admin moderation actions to the core audit trail
 * ({@code POST /internal/audit}, internal API key). Best effort — never fails the action.
 */
@Component
public class AuditClient {

    private static final Logger logger = LoggerFactory.getLogger(AuditClient.class);

    private final RestTemplate restTemplate;
    private final String coreInternalUrl;

    public AuditClient(RestTemplate restTemplate,
            @Value("${core.internal.url:http://localhost:8081/internal}") String coreInternalUrl) {
        this.restTemplate = restTemplate;
        this.coreInternalUrl = coreInternalUrl;
    }

    public void record(String actionType, String targetId, String details) {
        JwtPrincipal actor = OwnershipGuard.current();
        Map<String, String> event = new HashMap<>();
        event.put("actorId", actor != null ? actor.userId() : "unknown");
        event.put("actorUsername", actor != null ? actor.username() : "unknown");
        event.put("actionType", actionType);
        event.put("targetId", targetId);
        event.put("details", details);
        try {
            restTemplate.postForEntity(coreInternalUrl + "/audit", event, Void.class);
        } catch (Exception e) {
            logger.warn("Audit event {} for {} not delivered: {}", actionType, targetId, e.getMessage());
        }
    }
}
