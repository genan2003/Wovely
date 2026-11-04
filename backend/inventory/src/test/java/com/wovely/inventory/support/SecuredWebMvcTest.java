package com.wovely.inventory.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.wovely.inventory.security.CorsConfig;
import com.wovely.inventory.security.JwtUtils;
import com.wovely.inventory.security.OwnershipGuard;
import com.wovely.inventory.security.RestAuthHandlers;
import com.wovely.inventory.security.WebSecurityConfig;

/**
 * Base for inventory-service {@code @WebMvcTest} slices with the real security chain
 * (JWT + internal API key + ownership). Internal key in tests: {@value #INTERNAL_KEY}.
 */
@ActiveProfiles("test")
@Import({ WebSecurityConfig.class, JwtUtils.class, RestAuthHandlers.class, OwnershipGuard.class, CorsConfig.class })
public abstract class SecuredWebMvcTest {

    protected static final String AUTH = "Authorization";
    protected static final String INTERNAL_HEADER = "X-Internal-Api-Key";
    protected static final String INTERNAL_KEY = "test-internal-key";

    @Autowired
    protected MockMvc mvc;

    protected static String bearer(String token) {
        return TestTokens.bearer(token);
    }
}
