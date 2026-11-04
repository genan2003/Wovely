package com.wovely.inventory.security;

import java.util.List;

/**
 * Authenticated caller, built from the JWT issued by the core service
 * ({@code sub} = username, {@code uid} = user id, {@code roles} = granted roles),
 * or from the internal API key for service-to-service calls.
 */
public record JwtPrincipal(String userId, String username, List<String> roles) {

    public static final String SERVICE_ROLE = "ROLE_SERVICE";

    public static JwtPrincipal service() {
        return new JwtPrincipal("service", "service", List.of(SERVICE_ROLE));
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ROLE_ADMIN");
    }
}
