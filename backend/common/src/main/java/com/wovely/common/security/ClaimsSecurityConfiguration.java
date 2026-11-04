package com.wovely.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans for services that trust the JWT claims issued by core (products, inventory): the verifier,
 * the 401/403 JSON handlers and the {@code @ownership} guard used in {@code @PreAuthorize}.
 * Import it from the service's security config. Core issues tokens and reloads users from Mongo, so
 * it does not use this.
 */
@Configuration(proxyBeanMethods = false)
public class ClaimsSecurityConfiguration {

    @Bean
    JwtUtils jwtUtils() {
        return new JwtUtils();
    }

    @Bean
    RestAuthHandlers restAuthHandlers() {
        return new RestAuthHandlers();
    }

    @Bean("ownership")
    OwnershipGuard ownership() {
        return new OwnershipGuard();
    }
}
