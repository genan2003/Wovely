package com.wovely.inventory.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Ownership rules for method security, e.g.
 * {@code @PreAuthorize("@ownership.sellerOrAdmin(#sellerId)")}.
 */
@Component("ownership")
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
