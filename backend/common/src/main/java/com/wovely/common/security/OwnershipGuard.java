package com.wovely.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Ownership rules for method security, e.g.
 * {@code @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")}.
 */

public class OwnershipGuard {

    /** Admin, or a seller acting on their own {@code sellerId}. */
    public boolean sellerOrAdmin(String sellerId) {
        JwtPrincipal p = current();
        if (p == null) {
            return false;
        }
        return p.isAdmin() || (p.hasRole("ROLE_SELLER") && p.userId() != null && p.userId().equals(sellerId));
    }

    public static JwtPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof JwtPrincipal p ? p : null;
    }
}
