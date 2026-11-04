package com.wovely.wovely.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.wovely.wovely.exception.ApiException;
import com.wovely.wovely.security.services.UserDetailsImpl;

/**
 * Ownership rules. Usable from method security
 * ({@code @PreAuthorize("@ownership.selfOrAdmin(#userId)")}) and from services
 * ({@link #requireAnyOf(String...)} after loading an entity).
 */
@Component("ownership")
public class OwnershipGuard {

    /** Admin, or the caller acting on their own user id. */
    public boolean selfOrAdmin(String userId) {
        UserDetailsImpl me = current();
        return me != null && (isAdmin(me) || me.getId().equals(userId));
    }

    /** Admin, or a seller acting on their own seller id. */
    public boolean sellerOrAdmin(String sellerId) {
        UserDetailsImpl me = current();
        return me != null && (isAdmin(me) || (hasRole(me, "ROLE_SELLER") && me.getId().equals(sellerId)));
    }

    /** Throws 403 unless the caller is an admin or one of {@code allowedUserIds}. */
    public UserDetailsImpl requireAnyOf(String... allowedUserIds) {
        UserDetailsImpl me = requireUser();
        if (isAdmin(me)) {
            return me;
        }
        for (String id : allowedUserIds) {
            if (id != null && id.equals(me.getId())) {
                return me;
            }
        }
        throw ApiException.forbidden("You do not have access to this resource");
    }

    public UserDetailsImpl requireUser() {
        UserDetailsImpl me = current();
        if (me == null) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Authentication required");
        }
        return me;
    }

    public static UserDetailsImpl current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserDetailsImpl u ? u : null;
    }

    public static boolean isAdmin(UserDetailsImpl user) {
        return hasRole(user, "ROLE_ADMIN");
    }

    private static boolean hasRole(UserDetailsImpl user, String role) {
        return user.getAuthorities().stream().anyMatch(a -> role.equals(a.getAuthority()));
    }
}
